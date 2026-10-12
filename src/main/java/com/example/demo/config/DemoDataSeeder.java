package com.example.demo.config;

import com.example.demo.enums.Role;
import com.example.demo.model.entity.User;
import com.example.demo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 演示数据初始化：仅在 {@code demo.seed.enabled=true}（dev/test）时执行，生产必须保持关闭。
 *
 * <p>幂等：账号已存在则跳过；并发启动时依赖唯一索引兜底，落败方不会因冲突而启动失败。
 */
@Component
@ConditionalOnProperty(prefix = "demo.seed", name = "enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DemoProperties properties;

    public DemoDataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder, DemoProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        var seed = properties.seed();
        List.of(
                new SeedAccount(seed.adminUsername(), seed.adminPassword(), Set.of(Role.ADMIN, Role.USER)),
                new SeedAccount(seed.userUsername(), seed.userPassword(), Set.of(Role.USER))
        ).forEach(this::upsert);
    }

    private void upsert(SeedAccount account) {
        if (userRepository.existsByUsername(account.username())) {
            log.debug("演示账号已存在, 跳过: {}", account.username());
            return;
        }
        var user = User.register(account.username(), passwordEncoder.encode(account.password()), null, null, account.roles());
        try {
            // 每个账号各自一个事务；并发启动时唯一索引会让落败方抛异常，视为"另一个实例已创建"
            userRepository.save(user);
            log.info("已创建演示账号: {} (角色: {})", account.username(), account.roles());
        } catch (DataIntegrityViolationException e) {
            log.debug("演示账号已被其它实例创建, 跳过: {}", account.username());
        }
    }

    private record SeedAccount(String username, String password, Set<Role> roles) {
    }
}
