package com.example.demo.model.dto;

import com.example.demo.common.validation.MaxBytes;
import com.example.demo.common.validation.NotMasked;
import com.example.demo.common.validation.ValidPhone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * 当前登录用户自助修改资料（{@code PATCH /api/users/me}）。
 *
 * <p>不含 {@code status}：状态是管理动作，普通用户不能自行启用/禁用账号。
 */
public record UpdateProfileRequest(
        @Email(message = "邮箱格式不正确")
        @Size(max = 128)
        @NotMasked
        String email,

        @ValidPhone
        @NotMasked
        String phone,

        @Size(min = 8, message = "密码至少 8 位")
        @MaxBytes(value = 72, message = "密码不能超过 72 字节(BCrypt 输入上限)")
        String password
) {

    public boolean isEmpty() {
        return email == null && phone == null && password == null;
    }
}
