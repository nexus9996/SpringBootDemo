package com.example.demo;

import com.example.demo.model.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 回归测试：未配置 {@code demo.security.jwt.secret} 时，签发端与验签端必须共用同一把进程内随机密钥，
 * 否则"登录成功但令牌立刻失效"。
 *
 * <p>该场景只有在这种"密钥留空"的配置下才会暴露，固定密钥的常规测试覆盖不到。
 */
@ActiveProfiles("test")
@SpringBootTest(properties = "demo.security.jwt.secret=")
@AutoConfigureMockMvc
class RandomSigningKeyRoundTripTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void tokenIssuedWithGeneratedKeyIsAcceptedByTheSameInstance() throws Exception {
        var login = mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest("admin", "admin12345"))))
                .andExpect(status().isOk())
                .andReturn();
        var token = jsonMapper.readTree(login.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("accessToken").asString();

        var me = mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(jsonMapper.readTree(me.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("username").asString()).isEqualTo("admin");
    }
}
