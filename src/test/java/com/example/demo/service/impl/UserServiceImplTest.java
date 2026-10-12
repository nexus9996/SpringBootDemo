package com.example.demo.service.impl;

import com.example.demo.api.ErrorCode;
import com.example.demo.common.exception.BusinessException;
import com.example.demo.common.exception.NotFoundException;
import com.example.demo.converter.UserConverter;
import com.example.demo.enums.Role;
import com.example.demo.enums.UserStatus;
import com.example.demo.model.dto.CreateUserRequest;
import com.example.demo.model.dto.UpdateProfileRequest;
import com.example.demo.model.dto.UpdateUserRequest;
import com.example.demo.model.entity.User;
import com.example.demo.model.query.UserPageQuery;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class UserServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final UserConverter converter = new UserConverter();

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, passwordEncoder, converter);
    }

    @Test
    void createRejectsDuplicatedUsername() {
        given(userRepository.existsByUsername("alice")).willReturn(true);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.create(new CreateUserRequest("alice", "password123", null, null)))
                .satisfies(e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.USERNAME_ALREADY_EXISTS));

        then(userRepository).should(never()).save(any());
    }

    @Test
    void createStoresHashedPasswordAndReturnsMaskedView() {
        given(userRepository.existsByUsername("alice")).willReturn(false);
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        var dto = service.create(new CreateUserRequest("alice", "password123", "alice@example.com", "13812345678"));

        var saved = savedUser();
        assertThat(saved.getPassword()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.getPassword())).isTrue();
        assertThat(dto.username()).isEqualTo("alice");
        assertThat(dto.email()).isEqualTo("a***e@example.com");
        assertThat(dto.phone()).isEqualTo("138****5678");
        assertThat(dto.roles()).containsExactly(Role.USER);
    }

    @Test
    void getByIdFailsWhenUserMissing() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.getById(99L))
                .satisfies(e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND))
                .withMessageContaining("用户");
    }

    @Test
    void pageConvertsToPageResponseWithOneBasedPageNumber() {
        var query = new UserPageQuery(null, null, 2, 5, "username", "asc");
        var user = User.register("alice", "encoded", null, null, null);
        given(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(user), PageRequest.of(1, 5), 6));

        var page = service.page(query);

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(5);
        assertThat(page.totalElements()).isEqualTo(6);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.content()).singleElement().satisfies(dto -> assertThat(dto.username()).isEqualTo("alice"));
    }

    @Test
    void genuineContactChangeIsApplied() {
        var existing = User.register("alice", "encoded", "alice@example.com", "13812345678", null);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(existing));

        service.updateProfile("alice", new UpdateProfileRequest("brand.new@example.com", null, null));

        assertThat(existing.getEmail()).isEqualTo("brand.new@example.com");
        assertThat(existing.getPhone()).isEqualTo("13812345678");
    }

    @Test
    void updateRejectsRequestWithoutAnyChange() {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.update(1L, new UpdateUserRequest(null, null, null, null)))
                .satisfies(e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

        then(userRepository).should(never()).findById(any());
    }

    @Test
    void updateAppliesOnlyProvidedFields() {
        var existing = User.register("alice", "old-encoded", "old@example.com", "13800000000", Set.of(Role.USER));
        given(userRepository.findById(1L)).willReturn(Optional.of(existing));

        var dto = service.update(1L, new UpdateUserRequest("new@example.com", null, UserStatus.DISABLED, "brandnew123"));

        assertThat(dto.email()).isEqualTo("n***w@example.com");
        assertThat(dto.phone()).isEqualTo("138****0000");
        assertThat(dto.status()).isEqualTo(UserStatus.DISABLED);
        assertThat(passwordEncoder.matches("brandnew123", existing.getPassword())).isTrue();
        // 只依赖脏检查提交，不额外 save
        then(userRepository).should(never()).save(any());
    }

    @Test
    void updateProfileRejectsEmptyRequest() {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.updateProfile("alice", new UpdateProfileRequest(null, null, null)))
                .satisfies(e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void updateProfileChangesEmail() {
        var existing = User.register("alice", "encoded", "old@example.com", null, null);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(existing));

        var dto = service.updateProfile("alice", new UpdateProfileRequest("fresh@example.com", "13912345678", null));

        assertThat(dto.email()).isEqualTo("f***h@example.com");
        assertThat(dto.phone()).isEqualTo("139****5678");
    }

    @Test
    void deleteRemovesExistingUser() {
        var existing = User.register("alice", "encoded", null, null, null);
        given(userRepository.findById(1L)).willReturn(Optional.of(existing));

        service.delete(1L);

        then(userRepository).should().delete(existing);
    }

    @Test
    void deleteFailsForMissingUser() {
        given(userRepository.findById(2L)).willReturn(Optional.empty());

        assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> service.delete(2L));

        then(userRepository).should(never()).delete(any(User.class));
        then(userRepository).should(never()).deleteById(any());
    }

    @Test
    void getByUsernameTrimsNothingButFailsWhenAbsent() {
        given(userRepository.findByUsername(anyString())).willReturn(Optional.empty());

        assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> service.getByUsername("ghost"));
    }

    private User savedUser() {
        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(captor.capture());
        return captor.getValue();
    }
}
