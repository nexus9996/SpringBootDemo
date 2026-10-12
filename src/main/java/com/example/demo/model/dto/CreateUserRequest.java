package com.example.demo.model.dto;

import com.example.demo.common.validation.MaxBytes;
import com.example.demo.common.validation.ValidPhone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建用户请求（仅 ADMIN 可用）。
 *
 * <p>密码上限 72 字节：BCrypt 只取前 72 字节，超出部分会被静默忽略，因此在契约层就拒绝。
 */
public record CreateUserRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$", message = "用户名只能包含字母、数字、下划线，长度 3-32")
        String username,

        @NotBlank
        @Size(min = 8, message = "密码至少 8 位")
        @MaxBytes(value = 72, message = "密码不能超过 72 字节(BCrypt 输入上限)")
        String password,

        @Email(message = "邮箱格式不正确")
        @Size(max = 128)
        String email,

        @ValidPhone
        String phone
) {
}
