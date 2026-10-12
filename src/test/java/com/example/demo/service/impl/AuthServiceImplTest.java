package com.example.demo.service.impl;

import com.example.demo.api.ErrorCode;
import com.example.demo.common.exception.BusinessException;
import com.example.demo.config.DemoProperties;
import com.example.demo.enums.Role;
import com.example.demo.enums.UserStatus;
import com.example.demo.model.dto.LoginRequest;
import com.example.demo.model.entity.User;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AuthServiceImplTest {

    private static final String SECRET = "unit-test-secret-value-at-least-32-bytes-long";
    private static final Duration TTL = Duration.ofHours(2);
    private static final SecretKey KEY = new SecretKeySpec(SECRET.getBytes(), "HmacSHA256");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final DemoProperties properties = new DemoProperties(
            new DemoProperties.Security(new DemoProperties.Security.Jwt("https://demo-test.example.com", "demo-api", SECRET, TTL), false, true),
            new DemoProperties.Cors(List.of("http://localhost:5173")),
            new DemoProperties.Seed(false, "admin", "admin12345", "demo", "demo12345"));

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userRepository, passwordEncoder,
                NimbusJwtEncoder.withSecretKey(KEY).algorithm(MacAlgorithm.HS256).build(), properties);
    }

    @Test
    void issuesSignedTokenCarryingMinimalClaims() {
        var user = persistedUser(1L, "alice", "password123", Set.of(Role.ADMIN, Role.USER));
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));

        var response = service.issueToken(new LoginRequest("alice", "password123"));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.roles()).containsExactly(Role.ADMIN, Role.USER);
        assertThat(response.expiresInSeconds()).isEqualTo(TTL.toSeconds());
        assertThat(response.expiresAt()).isAfter(Instant.now().plus(Duration.ofMinutes(119)));

        var jwt = decode(response.accessToken());
        assertThat(jwt.getSubject()).isEqualTo("alice");
        assertThat(jwt.getIssuer().toString()).isEqualTo("https://demo-test.example.com");
        assertThat(((Number) jwt.getClaim("uid")).longValue()).isEqualTo(1L);
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN", "USER");
        assertThat(jwt.getAudience()).containsExactly("demo-api");
        assertThat(response.accessToken()).doesNotContain("password123");
    }

    @Test
    void wrongPasswordIsRejectedWithGenericCode() {
        var user = persistedUser(1L, "alice", "password123", null);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.issueToken(new LoginRequest("alice", "wrong-password")))
                .satisfies(e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.BAD_CREDENTIALS));
    }

    @Test
    void unknownUsernameIsIndistinguishableFromWrongPassword() {
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.issueToken(new LoginRequest("ghost", "whatever123")))
                .satisfies(e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.BAD_CREDENTIALS))
                .withMessage(ErrorCode.BAD_CREDENTIALS.message());
    }

    @Test
    void disabledAccountCannotObtainToken() {
        var user = persistedUser(2L, "alice", "password123", null);
        user.changeStatus(UserStatus.DISABLED);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.issueToken(new LoginRequest("alice", "password123")))
                .satisfies(e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.FORBIDDEN))
                .withMessageContaining("禁用");
    }

    @Test
    void usernameIsTrimmedBeforeLookup() {
        var user = persistedUser(3L, "alice", "password123", null);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));

        assertThat(service.issueToken(new LoginRequest("  alice  ", "password123")).username()).isEqualTo("alice");
    }

    private User persistedUser(Long id, String username, String rawPassword, Set<Role> roles) {
        var user = User.register(username, passwordEncoder.encode(rawPassword), null, null, roles);
        // 主键由数据库生成，测试里用反射补齐（BaseEntity 不提供 setter）
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Jwt decode(String token) {
        return NimbusJwtDecoder.withSecretKey(KEY)
                .macAlgorithm(MacAlgorithm.HS256)
                .build()
                .decode(token);
    }
}
