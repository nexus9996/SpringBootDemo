package com.example.demo.converter;

import com.example.demo.enums.Role;
import com.example.demo.enums.UserStatus;
import com.example.demo.model.dto.CreateUserRequest;
import com.example.demo.model.entity.User;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserConverterTest {

    private final UserConverter converter = new UserConverter();

    @Test
    void createsEntityWithNormalizedFields() {
        var request = new CreateUserRequest("  alice  ", "password123", "alice@example.com", "13812345678");

        var user = converter.toEntity(request, "encoded");

        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getPassword()).isEqualTo("encoded");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getPhone()).isEqualTo("13812345678");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getRoles()).containsExactly(Role.USER);
    }

    @Test
    void blankOptionalFieldsBecomeNull() {
        var request = new CreateUserRequest("alice", "password123", "  ", "");

        var user = converter.toEntity(request, "encoded");

        assertThat(user.getEmail()).isNull();
        assertThat(user.getPhone()).isNull();
    }

    @Test
    void dtoMasksSensitiveContactFields() {
        var user = User.register("alice", "encoded", "alice@example.com", "+8613812345678", Set.of(Role.ADMIN, Role.USER));

        var dto = converter.toDTO(user);

        assertThat(dto.username()).isEqualTo("alice");
        assertThat(dto.email()).isEqualTo("a***e@example.com");
        assertThat(dto.phone()).isEqualTo("+86138****5678");
        assertThat(dto.roles()).containsExactly(Role.ADMIN, Role.USER);
        assertThat(dto.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void dtoKeepsNullContactsNull() {
        var user = User.register("bob", "encoded", null, null, null);

        var dto = converter.toDTO(user);

        assertThat(dto.email()).isNull();
        assertThat(dto.phone()).isNull();
        assertThat(dto.roles()).containsExactly(Role.USER);
    }
}
