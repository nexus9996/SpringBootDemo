package com.example.demo.controller;

import com.example.demo.api.ApiResponse;
import com.example.demo.api.PageResponse;
import com.example.demo.model.dto.CreateUserRequest;
import com.example.demo.model.dto.UpdateProfileRequest;
import com.example.demo.model.dto.UpdateUserRequest;
import com.example.demo.model.dto.UserDTO;
import com.example.demo.model.query.UserPageQuery;
import com.example.demo.security.CurrentUserProvider;
import com.example.demo.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口。
 *
 * <p>授权分两层：角色级用 {@code @PreAuthorize}（ADMIN 管理他人），自助操作走 {@code /me}，
 * 身份来自 JWT（{@link CurrentUserProvider}），客户端无法通过参数伪造用户。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    public UserController(UserService userService, CurrentUserProvider currentUserProvider) {
        this.userService = userService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<UserDTO>> page(UserPageQuery query) {
        return ApiResponse.ok(userService.page(query));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserDTO> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.created(userService.create(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserDTO> me() {
        return ApiResponse.ok(userService.getByUsername(currentUserProvider.require().username()));
    }

    @PatchMapping("/me")
    public ApiResponse<UserDTO> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateProfile(currentUserProvider.require().username(), request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserDTO> getById(@PathVariable @Positive Long id) {
        return ApiResponse.ok(userService.getById(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserDTO> update(@PathVariable @Positive Long id, @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable @Positive Long id) {
        userService.delete(id);
        return ApiResponse.ok();
    }
}
