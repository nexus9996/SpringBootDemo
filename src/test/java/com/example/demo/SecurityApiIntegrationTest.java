package com.example.demo;

import com.example.demo.model.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 安全与运行时能力的端到端验证：令牌签发/校验、公开端点边界、CORS 预检、统一错误包体、虚拟线程生效。
 */
class SecurityApiIntegrationTest extends AbstractApiTest {

    /** 与 application-test.yaml 中的 demo.security.jwt.secret 一致，用于构造"签名合法但声明非法"的令牌。 */
    private static final String TEST_SECRET = "test-only-secret-value-long-enough-for-hs256-32bytes+";

    @Test
    void issuesTokenForSeededAdmin() throws Exception {
        var result = mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(ADMIN_USERNAME, ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        var data = data(result);
        assertThat(data.path("tokenType").asString()).isEqualTo("Bearer");
        assertThat(data.path("username").asString()).isEqualTo(ADMIN_USERNAME);
        assertThat(data.path("expiresInSeconds").asLong()).isEqualTo(3600L);
        assertThat(data.path("roles").toString()).contains("ADMIN", "USER");
        assertThat(data.path("accessToken").asString()).isNotBlank();
    }

    @Test
    void wrongPasswordIsRejectedWithoutRevealingAccountExistence() throws Exception {
        var wrongPassword = mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(ADMIN_USERNAME, "definitely-wrong"))))
                .andExpect(status().isUnauthorized())
                .andReturn();
        var unknownUser = mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("no-such-user", "definitely-wrong"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(code(wrongPassword)).isEqualTo(40101);
        assertThat(code(unknownUser)).isEqualTo(40101);
        assertThat(body(unknownUser).path("message").asString())
                .isEqualTo(body(wrongPassword).path("message").asString());
    }

    @Test
    void tamperedTokenIsRejectedWithBearerChallenge() throws Exception {
        var valid = userToken();
        var tampered = valid.substring(0, valid.length() - 4) + "AAAA";

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(tampered)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""))
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void tokenSignedWithForeignSecretIsRejected() throws Exception {
        var forged = signWith("a-totally-different-secret-value-32bytes",
                JwtClaimsSet.builder()
                        .issuer("https://demo.example.com")
                        .audience(List.of("demo-api"))
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                        .subject("attacker")
                        .claim("roles", List.of("ADMIN")));

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(forged)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void anonymousAccessReturns401Envelope() throws Exception {
        var result = mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andReturn();

        assertThat(code(result)).isEqualTo(40100);
        assertThat(body(result).path("message").asString()).isEqualTo("未认证或凭证已失效");
        assertThat(body(result).path("timestamp").isNumber()).isTrue();
    }

    @Test
    void healthAndOpenApiArePublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());

        var apiDocs = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var document = body(apiDocs);
        assertThat(document.path("paths").has("/api/orders")).isTrue();
        assertThat(document.path("components").path("securitySchemes").has("bearerAuth")).isTrue();

        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    @Test
    void otherActuatorEndpointsAreNotPublic() throws Exception {
        mockMvc.perform(get("/actuator/beans")).andExpect(status().isUnauthorized());
    }

    @Test
    void metricsRequireAdminRole() throws Exception {
        var forbidden = mockMvc.perform(get("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, bearer(userToken())))
                .andExpect(status().isForbidden())
                .andReturn();
        assertThat(code(forbidden)).isEqualTo(40300);

        mockMvc.perform(get("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk());
    }

    @Test
    void tokenForAnotherAudienceIsRejected() throws Exception {
        var token = encodeToken(claims -> claims.audience(List.of("some-other-api")));

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void stringValuedAudienceIsHandledWithoutServerError() throws Exception {
        // RFC 7519 允许 aud 为单个字符串；验签方不能因为类型与数组不同而抛 500
        var matching = encodeToken(claims -> claims.claim("aud", "demo-api"));
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(matching)))
                .andExpect(status().isOk());

        var mismatched = encodeToken(claims -> claims.claim("aud", "another-api"));
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(mismatched)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void tokenWithoutAudienceIsRejected() throws Exception {
        // 不复用带默认 aud 的 builder，构造一个真正没有 aud 的令牌
        var token = signWith(TEST_SECRET, JwtClaimsSet.builder()
                .issuer("https://demo.example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .subject("attacker"));

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenForAnotherIssuerIsRejected() throws Exception {
        var token = encodeToken(claims -> claims.issuer("https://evil.example.com"));

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unsupportedMethodAndMalformedJsonProduceEnvelope() throws Exception {
        var wrongMethod = mockMvc.perform(put("/api/auth/token").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andReturn();
        assertThat(code(wrongMethod)).isEqualTo(40500);

        var malformed = mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json }"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(code(malformed)).isEqualTo(40001);
    }

    @Test
    void runtimeEndpointProvesVirtualThreadsAreActive() throws Exception {
        var result = mockMvc.perform(get("/api/system/runtime").header(HttpHeaders.AUTHORIZATION, bearer(userToken())))
                .andExpect(status().isOk())
                .andReturn();

        var data = data(result);
        assertThat(data.path("applicationName").asString()).isEqualTo("Demo");
        assertThat(data.path("javaVersion").asString()).startsWith("25");
        // MockMvc 在测试线程上执行，请求线程是否为虚拟线程由 RealServerSmokeTest 在真实 Tomcat 上断言
        assertThat(data.path("requestOnVirtualThread").isBoolean()).isTrue();
        assertThat(data.path("asyncExecutorOnVirtualThread").asBoolean()).isTrue();
        assertThat(data.path("requestThreadName").asString()).isNotBlank();
    }

    /** 用测试密钥签发令牌，claims 可在默认声明之上做任意改动。 */
    private String encodeToken(java.util.function.UnaryOperator<JwtClaimsSet.Builder> mutator) {
        var defaults = JwtClaimsSet.builder()
                .issuer("https://demo.example.com")
                .audience(List.of("demo-api"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .subject("attacker")
                .claim("uid", 1L)
                .claim("roles", List.of("ADMIN"));
        return signWith(TEST_SECRET, mutator.apply(defaults));
    }

    private static String signWith(String secret, JwtClaimsSet.Builder claims) {
        var encoder = NimbusJwtEncoder
                .withSecretKey(new SecretKeySpec(secret.getBytes(), "HmacSHA256"))
                .algorithm(MacAlgorithm.HS256)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }

    @Test
    void corsPreflightIsAnsweredForConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/users")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                // 纯 Bearer 鉴权不带 Cookie，因此不放开凭据
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void corsPreflightIsRejectedForUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/users")
                        .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }
}
