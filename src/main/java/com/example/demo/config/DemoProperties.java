package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * {@code demo.*} 自定义配置（构造器绑定，启动即校验，配置缺项不会拖到运行时才暴露）。
 *
 * <p>默认值一律取"更安全的一侧"：开放注册默认关闭、H2 控制台默认关闭、演示数据默认不创建，
 * 只有显式开启的本地/测试 profile 才放宽。
 */
@ConfigurationProperties(prefix = "demo")
public record DemoProperties(
        Security security,
        Cors cors,
        Seed seed
) {

    public record Security(
            Jwt jwt,
            @DefaultValue("false") boolean allowH2Console,
            /** 开放注册开关：默认关闭，仅本地/测试 profile 显式打开。 */
            @DefaultValue("false") boolean allowRegistration) {

        public record Jwt(
                /** {@code iss} 必须是合法 URL（Spring Security 按 URL 校验签发者）。 */
                @DefaultValue("https://demo.example.com") String issuer,
                /** {@code aud} 声明，用于把令牌限定到本 API。 */
                @DefaultValue("demo-api") String audience,
                String secret,
                @DefaultValue("1h") Duration ttl
        ) {
        }
    }

    public record Cors(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {
    }

    /**
     * 仅用于本地/测试环境演示的初始账号；生产环境必须关闭（{@code demo.seed.enabled=false}）。
     */
    public record Seed(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("admin") String adminUsername,
            @DefaultValue("admin12345") String adminPassword,
            @DefaultValue("demo") String userUsername,
            @DefaultValue("demo12345") String userPassword
    ) {
    }
}
