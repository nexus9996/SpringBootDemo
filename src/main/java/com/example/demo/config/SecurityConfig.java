package com.example.demo.config;

import com.example.demo.api.ApiResponse;
import com.example.demo.api.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collection;

/**
 * 安全配置：无状态 JWT 资源服务器。
 *
 * <p>关键取舍：
 * <ul>
 *   <li>无 Session、无表单登录，因此关闭 CSRF —— 没有 Cookie 凭据就没有 CSRF 攻击面；</li>
 *   <li>401/403 也返回统一响应包体（{@link ApiResponse}），前端只需处理一套错误格式；</li>
 *   <li>H2 控制台默认关闭，仅在 {@code demo.security.allow-h2-console=true}（dev/test）时放行；</li>
 *   <li>角色级鉴权用 {@code @PreAuthorize}，数据级鉴权在服务层（见 {@code OrderService}）。</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/token",
            "/api/auth/register",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            DemoProperties properties,
                                            AuthenticationEntryPoint entryPoint,
                                            AccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> {
                    requests.requestMatchers(PUBLIC_ENDPOINTS).permitAll();
                    // metrics 等运维端点包含端点清单与耗时画像，只对 ADMIN 开放
                    requests.requestMatchers("/actuator/**").hasRole("ADMIN");
                    if (properties.security().allowH2Console()) {
                        requests.requestMatchers("/h2-console/**").permitAll();
                    }
                    requests.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        if (properties.security().allowH2Console()) {
            // H2 控制台使用 iframe，只有显式开启时才放宽同源限制
            http.headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));
        }
        return http.build();
    }

    /** JWT → 认证对象：把 {@code roles} 声明映射为 {@code ROLE_*} authority。 */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    /**
     * HS256 签名/验签密钥：**单例**，签发端与验签端必须共用同一个实例。
     *
     * <p>若未配置 {@code demo.security.jwt.secret}，则生成进程内随机密钥（不落盘）；
     * 因此两种情况都只在这里生成一次，避免"签发用一个随机密钥、验签用另一个"导致令牌全部失效。
     */
    @Bean
    SecretKey jwtSigningKey(DemoProperties properties) {
        var secret = properties.security().jwt().secret();
        if (secret == null || secret.isBlank()) {
            var generated = new byte[32];
            new SecureRandom().nextBytes(generated);
            log.warn("未配置 demo.security.jwt.secret, 本次启动使用进程内随机密钥; "
                    + "仅适用于本地开发(重启后已签发的令牌全部失效), 生产环境必须显式注入");
            return new SecretKeySpec(generated, "HmacSHA256");
        }
        var bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "demo.security.jwt.secret 至少需要 32 字节(HS256), 当前为 " + bytes.length + " 字节");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    /** 解码并校验签名算法、签发者(aud)、有效期。 */
    @Bean
    JwtDecoder jwtDecoder(DemoProperties properties, SecretKey jwtSigningKey) {
        var decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        var expectedAudience = properties.security().jwt().audience();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.security().jwt().issuer()),
                // aud 允许数组或单个字符串（RFC 7519 §4.1.3），两种形态都必须接受，不能因类型不匹配抛 500
                new JwtClaimValidator<Object>(JwtClaimNames.AUD, audience -> switch (audience) {
                    case null -> false;
                    case Collection<?> values -> values.contains(expectedAudience);
                    default -> expectedAudience.equals(audience);
                })));
        return decoder;
    }

    /** 本地签发令牌（演示用；生产应改由统一认证中心签发，本服务只做资源服务器）。 */
    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSigningKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 未认证 / 令牌无效 → 401 + 统一包体。 */
    @Bean
    AuthenticationEntryPoint authenticationEntryPoint(JsonMapper jsonMapper) {
        return (request, response, authException) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, request.getHeader(HttpHeaders.AUTHORIZATION) == null
                    ? "Bearer"
                    : "Bearer error=\"invalid_token\"");
            writeError(response, jsonMapper, ErrorCode.UNAUTHORIZED);
        };
    }

    /** 已认证但权限不足 → 403 + 统一包体。 */
    @Bean
    AccessDeniedHandler accessDeniedHandler(JsonMapper jsonMapper) {
        return (request, response, accessDeniedException) -> writeError(response, jsonMapper, ErrorCode.FORBIDDEN);
    }

    private static void writeError(HttpServletResponse response, JsonMapper jsonMapper, ErrorCode errorCode)
            throws IOException {
        response.setStatus(errorCode.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), ApiResponse.fail(errorCode));
    }


}
