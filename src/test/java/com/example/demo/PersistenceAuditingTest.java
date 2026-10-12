package com.example.demo;

import com.example.demo.enums.Role;
import com.example.demo.enums.UserStatus;
import com.example.demo.model.dto.CreateUserRequest;
import com.example.demo.model.dto.UpdateUserRequest;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审计与乐观锁：验证 Spring Data JPA Auditing 与 JWT 主体的联动，以及 {@code @Version} 行为。
 */
@ActiveProfiles("test")
@SpringBootTest
class PersistenceAuditingTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void timestampsActorsAndVersionAreManagedByTheFramework() {
        authenticateAs("auditor");
        var username = "audit" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        var created = userService.create(new CreateUserRequest(username, "password123", null, null));
        var persisted = userRepository.findById(created.id()).orElseThrow();

        assertThat(persisted.getCreateTime()).isNotNull();
        assertThat(persisted.getUpdateTime()).isNotNull();
        assertThat(persisted.getCreateTime()).isBeforeOrEqualTo(persisted.getUpdateTime());
        assertThat(persisted.getCreatedBy()).isEqualTo("auditor");
        assertThat(persisted.getUpdatedBy()).isEqualTo("auditor");
        assertThat(persisted.getVersion()).isZero();
        assertThat(userService.getById(created.id()).roles()).containsExactly(Role.USER);

        userService.update(created.id(), new UpdateUserRequest(null, "13712345678", UserStatus.LOCKED, null));
        var updated = userRepository.findById(created.id()).orElseThrow();

        assertThat(updated.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(updated.getPhone()).isEqualTo("13712345678");
        assertThat(updated.getVersion()).isEqualTo(1L);
        assertThat(updated.getCreateTime()).isEqualTo(persisted.getCreateTime());
        assertThat(updated.getUpdateTime()).isAfterOrEqualTo(persisted.getUpdateTime());
    }

    @Test
    void unauthenticatedWritesAreAttributedToSystem() {
        SecurityContextHolder.clearContext();
        var username = "sys" + UUID.randomUUID().toString().replace("-", "").substring(0, 9);

        var created = userService.create(new CreateUserRequest(username, "password123", null, null));

        assertThat(userRepository.findById(created.id()).orElseThrow().getCreatedBy()).isEqualTo("system");
    }

    private static void authenticateAs(String subject) {
        var jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(subject)
                .claim("uid", 4242L)
                .claim("roles", List.of("ADMIN"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), subject));
    }
}
