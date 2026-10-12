package com.example.demo.controller;

import com.example.demo.api.ApiResponse;
import com.example.demo.api.ErrorCode;
import com.example.demo.common.exception.BusinessException;
import com.example.demo.config.DemoProperties;
import com.example.demo.model.dto.CreateUserRequest;
import com.example.demo.model.dto.LoginRequest;
import com.example.demo.model.dto.TokenResponse;
import com.example.demo.model.dto.UserDTO;
import com.example.demo.service.AuthService;
import com.example.demo.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 *
 * <p>演示项目内置签发端（HS256），生产环境应改为对接统一认证中心（OIDC），
 * 届时只需配置 {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri} 并移除本地 {@code JwtEncoder}。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final DemoProperties properties;

    public AuthController(AuthService authService, UserService userService, DemoProperties properties) {
        this.authService = authService;
        this.userService = userService;
        this.properties = properties;
    }

    @PostMapping("/token")
    public ApiResponse<TokenResponse> token(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.issueToken(request));
    }

    /**
     * 开放注册：只能创建普通用户（{@code Role.USER}）。
     *
     * <p>由 {@code demo.security.allow-registration} 控制，生产环境应关闭（默认仅本地/测试开启）。
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserDTO> register(@Valid @RequestBody CreateUserRequest request) {
        if (!properties.security().allowRegistration()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "开放注册已关闭, 请联系管理员创建账号");
        }
        return ApiResponse.created(userService.create(request));
    }
}
