package com.example.demo;

import com.example.demo.model.dto.CreateUserRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 生产开关：{@code demo.security.allow-registration=false} 时开放注册必须关闭
 * （否则任何人都能自建账号）。
 */
@ActiveProfiles("test")
@SpringBootTest(properties = "demo.security.allow-registration=false")
@AutoConfigureMockMvc
class RegistrationToggleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void registrationEndpointIsForbiddenWhenDisabled() throws Exception {
        var username = "signup" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        var payload = jsonMapper.writeValueAsString(new CreateUserRequest(username, "password123", null, null));

        var result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andReturn();

        var body = jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.path("code").asInt()).isEqualTo(40300);
        assertThat(body.path("message").asString()).contains("开放注册已关闭");
    }
}
