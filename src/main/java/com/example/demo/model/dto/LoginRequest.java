package com.example.demo.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求（换取 JWT）。
 */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
