package com.example.demo.model.dto;

import com.example.demo.common.validation.MaxBytes;
import com.example.demo.common.validation.NotMasked;
import com.example.demo.common.validation.ValidPhone;
import com.example.demo.enums.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * 更新用户请求（仅 ADMIN 可用）。
 *
 * <p>PATCH 语义：字段为 {@code null} 表示"不修改"，因此没有 {@code @NotBlank} 之类的必填约束。
 */
public record UpdateUserRequest(
        @Email(message = "邮箱格式不正确")
        @Size(max = 128)
        @NotMasked
        String email,

        @ValidPhone
        @NotMasked
        String phone,

        UserStatus status,

        @Size(min = 8, message = "密码至少 8 位")
        @MaxBytes(value = 72, message = "密码不能超过 72 字节(BCrypt 输入上限)")
        String password
) {

    /** 是否为没有任何修改意图的空请求。 */
    public boolean isEmpty() {
        return email == null && phone == null && status == null && password == null;
    }
}
