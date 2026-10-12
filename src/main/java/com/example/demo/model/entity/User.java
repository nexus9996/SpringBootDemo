package com.example.demo.model.entity;

import com.example.demo.enums.Role;
import com.example.demo.enums.UserStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 用户聚合根。
 *
 * <p>不使用 Lombok：显式 getter + 表意变更方法（{@code changeProfile}/{@code changeStatus}），
 * 保证状态只能通过业务语义被修改；密码只以 BCrypt 摘要存在，且不参与序列化。
 */
@Entity
@Table(name = "t_user", uniqueConstraints = @UniqueConstraint(name = "uk_t_user_username", columnNames = "username"))
public class User extends BaseEntity {

    @Column(nullable = false, length = 32)
    private String username;

    @JsonIgnore
    @Column(nullable = false, length = 100)
    private String password;

    @Column(length = 128)
    private String email;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status = UserStatus.ACTIVE;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "t_user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    protected User() {
        // JPA 需要
    }

    /**
     * 注册新用户；未指定角色时默认 {@link Role#USER}。
     */
    public static User register(String username, String encodedPassword, String email, String phone, Set<Role> roles) {
        var user = new User();
        user.username = Objects.requireNonNull(username, "username").trim();
        user.password = Objects.requireNonNull(encodedPassword, "encodedPassword");
        user.email = normalize(email);
        user.phone = normalize(phone);
        user.roles = roles == null || roles.isEmpty() ? EnumSet.of(Role.USER) : EnumSet.copyOf(roles);
        return user;
    }

    /** 重置密码，入参必须是已编码的摘要。 */
    public void changePassword(String encodedPassword) {
        this.password = Objects.requireNonNull(encodedPassword, "encodedPassword");
    }

    public void changeProfile(String email, String phone) {
        this.email = normalize(email);
        this.phone = normalize(phone);
    }

    public void changeStatus(UserStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public void grantRole(Role role) {
        this.roles.add(Objects.requireNonNull(role, "role"));
    }

    public boolean isActive() {
        return status.isActive();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public String getUsername() {
        return username;
    }

    @JsonIgnore
    public String getPassword() {
        return password;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    /** 调试/日志用，绝不包含凭据。 */
    @Override
    public String toString() {
        return "User{id=%s, username='%s', status=%s}".formatted(getId(), username, status);
    }
}
