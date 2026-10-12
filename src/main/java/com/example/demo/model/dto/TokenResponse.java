package com.example.demo.model.dto;

import com.example.demo.enums.Role;

import java.time.Instant;
import java.util.List;

/**
 * 访问令牌响应。
 */
public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        Instant expiresAt,
        String username,
        List<Role> roles
) {

    public static final String BEARER = "Bearer";
}
