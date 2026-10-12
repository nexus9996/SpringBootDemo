package com.example.demo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置：声明 Bearer 认证方案，Swagger UI 可直接输入 JWT 调试受保护接口。
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI demoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Demo API")
                        .version("v1")
                        .description("""
                                Spring Boot 4 / Java 25 业务架构示例。

                                鉴权流程：先调用 `POST /api/auth/token` 获取 JWT，再点击右上角 Authorize 填入令牌。
                                dev/test 环境默认账号：admin/admin12345（ADMIN）、demo/demo12345（USER）。
                                """)
                        .license(new License().name("Apache-2.0")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("填入 /api/auth/token 返回的 accessToken")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
