package com.example.demo;

import com.example.demo.model.dto.CreateUserRequest;
import com.example.demo.model.dto.LoginRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 集成测试基类：真实启动应用上下文（H2 + Flyway + 安全过滤器链 + JPA 审计），用统一响应包体断言。
 *
 * <p>中文断言一律通过 {@link #body(MvcResult)} 以 UTF-8 解析，避免 MockMvc 默认字符集带来的假失败。
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractApiTest {

    protected static final String ADMIN_USERNAME = "admin";
    protected static final String ADMIN_PASSWORD = "admin12345";
    protected static final String DEMO_USERNAME = "demo";
    protected static final String DEMO_PASSWORD = "demo12345";
    protected static final String REGISTERED_PASSWORD = "password123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper jsonMapper;

    protected String json(Object value) {
        return jsonMapper.writeValueAsString(value);
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    protected JsonNode data(MvcResult result) throws Exception {
        return body(result).path("data");
    }

    protected int code(MvcResult result) throws Exception {
        return body(result).path("code").asInt();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    protected String adminToken() throws Exception {
        return token(ADMIN_USERNAME, ADMIN_PASSWORD);
    }

    protected String userToken() throws Exception {
        return token(DEMO_USERNAME, DEMO_PASSWORD);
    }

    protected String token(String username, String password) throws Exception {
        var result = mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).path("accessToken").asString();
    }

    /** 开放注册一个普通用户并返回其响应数据节点。 */
    protected JsonNode registerUser(String username) throws Exception {
        var result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateUserRequest(username, REGISTERED_PASSWORD, username + "@example.com", "13812345678"))))
                .andExpect(status().isCreated())
                .andReturn();
        return data(result);
    }

    protected String uniqueUsername(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
