package com.example.demo.model.dto;

import com.example.demo.enums.Role;
import com.example.demo.enums.UserStatus;

import java.time.Instant;
import java.util.List;

/**
 * 用户响应 DTO：手机号与邮箱已脱敏，密码永不出现在任何响应中。
 */
public record UserDTO(
        Long id,
        String username,
        String email,
        String phone,
        UserStatus status,
        List<Role> roles,
        Instant createTime
) {
}
