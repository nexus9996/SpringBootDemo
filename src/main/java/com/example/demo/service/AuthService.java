package com.example.demo.service;

import com.example.demo.model.dto.LoginRequest;
import com.example.demo.model.dto.TokenResponse;

/**
 * 认证服务：校验凭据并签发访问令牌。
 */
public interface AuthService {

    TokenResponse issueToken(LoginRequest request);
}
