package com.example.demo.security;

import com.example.demo.enums.Role;

import java.util.Set;

/**
 * 当前请求的调用者身份（来自 JWT 声明），服务层据此做数据级鉴权，不依赖 Web 层对象。
 */
public record AuthenticatedUser(Long id, String username, Set<Role> roles) {

    public boolean isAdmin() {
        return roles.contains(Role.ADMIN);
    }
}
