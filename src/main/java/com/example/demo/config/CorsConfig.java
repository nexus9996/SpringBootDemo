package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 跨域配置。
 *
 * <p>只暴露一个 {@link CorsConfigurationSource}：Spring Security 的 {@code CorsFilter} 会使用它处理预检请求，
 * 从而避免"WebMvc 配置与安全过滤器各配一套 CORS"导致的预检被 401 拦截问题。
 * 允许来源通过 {@code demo.cors.allowed-origins} 注入（逗号分隔），不使用通配符。
 */
@Configuration
public class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(DemoProperties properties) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.cors().allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // 纯 Bearer 令牌鉴权（无 Cookie/Session），无需携带凭据
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
