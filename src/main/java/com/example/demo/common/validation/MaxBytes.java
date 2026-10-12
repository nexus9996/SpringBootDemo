package com.example.demo.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限制字符串的 <b>UTF-8 字节</b> 长度。
 *
 * <p>用于密码等受算法字节上限约束的字段：BCrypt 只处理前 72 字节，多字节字符（中文等）在
 * {@code @Size} 的字符口径下会被静默截断，导致文档承诺与真实强度不一致。
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxBytesValidator.class)
public @interface MaxBytes {

    int value();

    String message() default "长度不能超过 {value} 字节";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
