package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 上下文启动冒烟：同时覆盖 Flyway 迁移、JPA 映射校验（ddl-auto=validate）、安全与 Web 层装配。
 */
@ActiveProfiles("test")
@SpringBootTest
class DemoApplicationTests {

    @Test
    void contextLoads() {
    }
}
