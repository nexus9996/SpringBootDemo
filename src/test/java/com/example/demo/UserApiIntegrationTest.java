package com.example.demo;

import com.example.demo.model.dto.CreateUserRequest;
import com.example.demo.model.dto.UpdateProfileRequest;
import com.example.demo.model.dto.UpdateUserRequest;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户接口端到端：分页/排序白名单、字段校验、脱敏、404/409/403 语义、自助端点。
 */
class UserApiIntegrationTest extends AbstractApiTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void adminCanListUsersWithPaging() throws Exception {
        var result = mockMvc.perform(get("/api/users")
                        .param("page", "1").param("size", "5")
                        .param("sortBy", "username").param("sortDir", "asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andReturn();

        var data = data(result);
        assertThat(data.path("page").asInt()).isEqualTo(1);
        assertThat(data.path("size").asInt()).isEqualTo(5);
        assertThat(data.path("totalElements").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(data.path("content").isArray()).isTrue();

        // 密码字段绝不出现在任何响应中
        mockMvc.perform(get("/api/users").param("size", "5").header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].password").doesNotExist());
    }

    @Test
    void paginationDefaultsToOneBasedFirstPage() throws Exception {
        var result = mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(data(result).path("page").asInt()).isEqualTo(1);
        assertThat(data(result).path("size").asInt()).isEqualTo(20);
    }

    @Test
    void createReturnsCreatedAndMasksContactsInReads() throws Exception {
        var username = uniqueUsername("walker");

        var created = mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateUserRequest(username, "password123", "walker@example.com", "13812345678"))))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(code(created)).isEqualTo(201);
        var data = data(created);
        assertThat(data.path("username").asString()).isEqualTo(username);
        assertThat(data.path("email").asString()).isEqualTo("w***r@example.com");
        assertThat(data.path("phone").asString()).isEqualTo("138****5678");
        assertThat(data.path("status").asString()).isEqualTo("ACTIVE");
        assertThat(data.path("roles").toString()).isEqualTo("[\"USER\"]");
        assertThat(data.path("createTime").asString()).isNotBlank();

        var found = mockMvc.perform(get("/api/users")
                        .param("keyword", username)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(data(found).path("content").get(0).path("username").asString()).isEqualTo(username);
    }

    @Test
    void duplicateUsernameReturns409() throws Exception {
        var username = uniqueUsername("dupuser");
        var payload = json(new CreateUserRequest(username, "password123", null, null));

        mockMvc.perform(post("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());

        var conflict = mockMvc.perform(post("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict())
                .andReturn();

        assertThat(code(conflict)).isEqualTo(40901);
        assertThat(body(conflict).path("message").asString()).contains(username);
    }

    @Test
    void invalidPayloadReturns400WithFieldDetails() throws Exception {
        var result = mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateUserRequest("no", "short", "not-an-email", "12345"))))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(code(result)).isEqualTo(40000);
        var message = body(result).path("message").asString();
        assertThat(message).contains("username").contains("password").contains("email").contains("phone");
    }

    @Test
    void unknownUserReturns404Envelope() throws Exception {
        var result = mockMvc.perform(get("/api/users/99999999").header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(code(result)).isEqualTo(40400);
        assertThat(body(result).path("message").asString()).contains("用户");
    }

    @Test
    void nonPositivePathIdReturns400() throws Exception {
        var result = mockMvc.perform(get("/api/users/-1").header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(code(result)).isEqualTo(40000);
    }

    @Test
    void unknownSortFieldReturns400() throws Exception {
        var result = mockMvc.perform(get("/api/users")
                        .param("sortBy", "password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(body(result).path("message").asString()).contains("排序字段");
    }

    @Test
    void unknownPathReturns404Envelope() throws Exception {
        var result = mockMvc.perform(get("/api/definitely-not-here").header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(code(result)).isEqualTo(40400);
    }

    @Test
    void userRoleCannotListUsers() throws Exception {
        var result = mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(userToken())))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(code(result)).isEqualTo(40300);
    }

    @Test
    void meReturnsOwnProfile() throws Exception {
        var result = mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(userToken())))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(data(result).path("username").asString()).isEqualTo(DEMO_USERNAME);
        assertThat(data(result).path("roles").toString()).isEqualTo("[\"USER\"]");
    }

    @Test
    void userCanPatchOwnProfile() throws Exception {
        var username = uniqueUsername("selfpatch");
        registerUser(username);
        var token = token(username, REGISTERED_PASSWORD);

        var updated = mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateProfileRequest("fresh.mail@example.com", "13912345678", null))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(data(updated).path("email").asString()).isEqualTo("f***l@example.com");
        assertThat(data(updated).path("phone").asString()).isEqualTo("139****5678");

        var reloaded = mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(data(reloaded).path("email").asString()).isEqualTo("f***l@example.com");
    }

    @Test
    void maskedContactsReadFromTheApiAreRejectedOnWrite() throws Exception {
        var username = uniqueUsername("maskround");
        registerUser(username);
        var token = token(username, REGISTERED_PASSWORD);

        var before = data(mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn());
        var maskedEmail = before.path("email").asString();
        var maskedPhone = before.path("phone").asString();
        assertThat(maskedEmail).contains("***");
        assertThat(maskedPhone).contains("****");

        // 前端把读到的脱敏值原样回填（例如只改手机号却整体提交）：契约层必须明确拒绝，且不得写库
        var rejected = mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateProfileRequest(maskedEmail, maskedPhone, null))))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(body(rejected).path("message").asString()).contains("脱敏");

        var stored = userRepository.findByUsername(username).orElseThrow();
        assertThat(stored.getEmail()).isEqualTo(username + "@example.com");
        assertThat(stored.getPhone()).isEqualTo("13812345678");

        // 真实修改仍然生效
        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateProfileRequest("real.change@example.com", null, null))))
                .andExpect(status().isOk());
        assertThat(userRepository.findByUsername(username).orElseThrow().getEmail())
                .isEqualTo("real.change@example.com");
    }

    @Test
    void emptyPatchIsRejected() throws Exception {
        var result = mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(body(result).path("message").asString()).contains("至少需要提供一个待修改字段");
    }

    @Test
    void adminCanDisableUserAndDisabledUserCannotLogin() throws Exception {
        var username = uniqueUsername("tobedisabled");
        var registered = registerUser(username);
        var id = registered.path("id").asLong();
        var issuedBeforeDisabling = token(username, REGISTERED_PASSWORD);

        mockMvc.perform(patch("/api/users/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateUserRequest(null, null, com.example.demo.enums.UserStatus.DISABLED, null))))
                .andExpect(status().isOk());

        var login = mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new com.example.demo.model.dto.LoginRequest(registered.path("username").asString(), REGISTERED_PASSWORD))))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(code(login)).isEqualTo(40300);
        assertThat(body(login).path("message").asString()).contains("禁用");

        // 禁用立即生效：之前签发的令牌同样不可继续使用（身份/状态每次请求回源数据库）
        var withOldToken = mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(issuedBeforeDisabling)))
                .andExpect(status().isForbidden())
                .andReturn();
        assertThat(code(withOldToken)).isEqualTo(40300);
        assertThat(body(withOldToken).path("message").asString()).contains("禁用");
    }

    @Test
    void adminCanDeleteUser() throws Exception {
        var registered = registerUser(uniqueUsername("tobedeleted"));
        var id = registered.path("id").asLong();

        mockMvc.perform(delete("/api/users/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isNotFound());
    }

    @Test
    void openRegistrationCreatesPlainUserAndOmitsNullFields() throws Exception {
        var username = uniqueUsername("signup");
        var result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateUserRequest(username, "password123", null, null))))
                .andExpect(status().isCreated())
                .andReturn();

        var data = data(result);
        assertThat(data.path("roles").toString()).isEqualTo("[\"USER\"]");
        // NON_NULL：空字段不出现在包体中
        assertThat(data.has("email")).isFalse();
        assertThat(data.has("phone")).isFalse();
    }
}
