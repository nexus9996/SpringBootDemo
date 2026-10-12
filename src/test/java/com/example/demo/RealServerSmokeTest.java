package com.example.demo;

import com.example.demo.model.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实容器端到端验证：以 RANDOM_PORT 启动内嵌 Tomcat，用真实 HTTP 请求断言。
 *
 * <p>这是 MockMvc 无法覆盖的部分：请求线程是否真的是虚拟线程（{@code spring.threads.virtual.enabled}）、
 * 真实 HTTP 状态码与响应头、压缩/连接等容器行为。
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealServerSmokeTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void requestThreadsAreVirtualThreads() {
        var runtime = getJson("/api/system/runtime", token("admin", "admin12345"));

        assertThat(runtime.path("applicationName").asString()).isEqualTo("Demo");
        assertThat(runtime.path("javaVersion").asString()).startsWith("25");
        assertThat(runtime.path("requestOnVirtualThread").asBoolean())
                .as("Tomcat 请求线程应为虚拟线程")
                .isTrue();
        assertThat(runtime.path("asyncExecutorOnVirtualThread").asBoolean())
                .as("applicationTaskExecutor 应运行在虚拟线程上")
                .isTrue();
    }

    @Test
    void authenticatedRequestUsesRealHttpAndReturnsOwnProfile() {
        var profile = getJson("/api/users/me", token("demo", "demo12345"));

        assertThat(profile.path("username").asString()).isEqualTo("demo");
        assertThat(profile.path("roles").toString()).isEqualTo("[\"USER\"]");
    }

    @Test
    void apiRejectsAnonymousRequestsOverRealHttp() {
        var status = client.get().uri("/api/users")
                .exchange((request, response) -> response.getStatusCode().value());

        assertThat(status).isEqualTo(401);
    }

    @Test
    void healthEndpointIsReachableWithoutCredentials() {
        var health = client.get().uri("/actuator/health").retrieve().body(String.class);

        assertThat(health).contains("\"status\":\"UP\"");
    }

    @Test
    void orderLifecycleWorksOverRealHttp() {
        var userToken = token("demo", "demo12345");
        var created = postJson("/api/orders", userToken, """
                {"remark":"真实 HTTP 下单","items":[{"productName":"显示器","unitPrice":1299.00,"quantity":1}]}
                """);
        var orderId = created.path("id").asLong();

        var paid = postJson("/api/orders/" + orderId + "/pay", userToken, null);
        var shipped = postJson("/api/orders/" + orderId + "/ship", token("admin", "admin12345"), null);

        assertThat(created.path("status").asString()).isEqualTo("CREATED");
        assertThat(created.path("totalAmount").asDouble()).isEqualTo(1299.00);
        assertThat(paid.path("status").asString()).isEqualTo("PAID");
        assertThat(shipped.path("status").asString()).isEqualTo("SHIPPED");
    }

    private String token(String username, String password) {
        var payload = jsonMapper.writeValueAsString(new LoginRequest(username, password));
        var response = client.post().uri("/api/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
        return jsonMapper.readTree(response).path("data").path("accessToken").asString();
    }

    private JsonNode getJson(String path, String token) {
        var response = client.get().uri(path)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class);
        return jsonMapper.readTree(response).path("data");
    }

    private JsonNode postJson(String path, String token, String body) {
        var spec = client.post().uri(path).header("Authorization", "Bearer " + token);
        var response = (body == null ? spec : spec.contentType(MediaType.APPLICATION_JSON).body(body))
                .retrieve()
                .body(String.class);
        return jsonMapper.readTree(response).path("data");
    }
}
