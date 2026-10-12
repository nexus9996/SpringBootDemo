package com.example.demo.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;

/**
 * JSON 序列化配置（Spring Boot 4 使用 Jackson 3：{@code tools.jackson}，注解仍在 {@code com.fasterxml.jackson.annotation}）。
 *
 * <p>通过 {@link JsonMapperBuilderCustomizer} 定制 Boot 自动配置的 {@code JsonMapper}，
 * 而不是自己 {@code new} 一个 ObjectMapper —— 后者会让 MVC 消息转换器与容器内其他组件使用两个不同实例。
 *
 * <p>约定：
 * <ul>
 *   <li>{@code NON_NULL}：响应不输出 {@code null} 字段，包体更紧凑；</li>
 *   <li>忽略未知字段：客户端多传字段不会导致 400，便于接口前向兼容；</li>
 *   <li>时间类型默认按 ISO-8601 字符串输出（Jackson 3 默认，无需额外配置）。</li>
 * </ul>
 */
@Configuration
public class JsonConfig {

    @Bean
    JsonMapperBuilderCustomizer jsonMapperBuilderCustomizer() {
        return builder -> builder
                .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
