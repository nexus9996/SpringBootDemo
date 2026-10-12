package com.example.demo.security;

import com.example.demo.api.ErrorCode;
import com.example.demo.common.exception.BusinessException;
import com.example.demo.enums.Role;
import com.example.demo.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 从 SecurityContext 读取当前调用者。
 *
 * <p>受保护端点由 Spring Security 保证存在已认证的 {@link Jwt} 主体；缺失或声明非法时
 * 视为未认证并返回 401，而不是静默降级为匿名。
 *
 * <p>身份与账号状态每次请求都回源数据库（主键查询）：这样"禁用/删除账号"能立即生效，
 * 而不是等令牌过期。角色仍取自令牌声明，因此权限变更需重新签发令牌（见 README 生产注意事项）。
 */
@Component
public class CurrentUserProvider {

    private final UserRepository userRepository;

    public CurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AuthenticatedUser require() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.message());
        }
        var userId = userId(jwt);
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "账号不存在或已被删除"));
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已%s".formatted(user.getStatus().label()));
        }
        return new AuthenticatedUser(user.getId(), user.getUsername(), roles(jwt));
    }

    private static Long userId(Jwt jwt) {
        return switch (jwt.getClaim("uid")) {
            case Number number -> number.longValue();
            case null -> throw new BusinessException(ErrorCode.UNAUTHORIZED, "访问令牌缺少 uid 声明");
            default -> throw new BusinessException(ErrorCode.UNAUTHORIZED, "访问令牌的 uid 声明格式不正确");
        };
    }

    private static Set<Role> roles(Jwt jwt) {
        List<String> claimed = jwt.getClaimAsStringList("roles");
        if (claimed == null || claimed.isEmpty()) {
            return Set.of();
        }
        var roles = new LinkedHashSet<Role>(claimed.size());
        for (var name : claimed) {
            try {
                roles.add(Role.valueOf(name));
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "访问令牌包含未知角色: " + name);
            }
        }
        return Set.copyOf(roles);
    }
}
