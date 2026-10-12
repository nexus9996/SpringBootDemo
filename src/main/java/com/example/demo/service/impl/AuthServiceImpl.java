package com.example.demo.service.impl;

import com.example.demo.api.ErrorCode;
import com.example.demo.common.exception.BusinessException;
import com.example.demo.config.DemoProperties;
import com.example.demo.enums.Role;
import com.example.demo.model.dto.LoginRequest;
import com.example.demo.model.dto.TokenResponse;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 认证服务实现。
 *
 * <p>安全细节：
 * <ul>
 *   <li>用户名不存在与密码错误返回<b>同一个</b>错误码，且都执行一次 BCrypt 比较（消除时间差），
 *       避免通过响应时间枚举账号；</li>
 *   <li>只有 {@code ACTIVE} 状态可以换取令牌；</li>
 *   <li>令牌只携带 {@code uid}/{@code roles} 等最小声明，权限判断不依赖数据库往返。</li>
 * </ul>
 */
@Service
public class AuthServiceImpl implements AuthService {

    /** 账号不存在时也要付出的 BCrypt 计算量，使"存在/不存在"两条路径耗时一致。 */
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final DemoProperties properties;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           JwtEncoder jwtEncoder, DemoProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public TokenResponse issueToken(LoginRequest request) {
        var user = userRepository.findByUsername(request.username().trim()).orElse(null);
        var passwordMatches = passwordEncoder.matches(request.password(),
                user == null ? DUMMY_PASSWORD_HASH : user.getPassword());
        if (user == null || !passwordMatches) {
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已%s, 无法登录".formatted(user.getStatus().label()));
        }

        var now = Instant.now();
        var expiresAt = now.plus(properties.security().jwt().ttl());
        var roles = user.getRoles().stream().sorted().toList();
        var claims = JwtClaimsSet.builder()
                .issuer(properties.security().jwt().issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .audience(List.of(properties.security().jwt().audience()))
                .claim("uid", user.getId())
                .claim("roles", roles.stream().map(Role::name).toList())
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();

        var token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims));
        return new TokenResponse(
                token.getTokenValue(),
                TokenResponse.BEARER,
                Duration.between(now, expiresAt).toSeconds(),
                expiresAt,
                user.getUsername(),
                List.copyOf(roles));
    }
}
