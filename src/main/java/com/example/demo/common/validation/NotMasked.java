package com.example.demo.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 拒绝"回填脱敏值"。
 *
 * <p>出站 DTO 里的手机号/邮箱是脱敏形态（{@code 138****5678} / {@code a***e@example.com}）。
 * 客户端若把读到的值原样提交回来，必须在契约层明确拒绝：否则脱敏串会被当成真实值写库
 * （{@code @Email} 允许 {@code *} 出现在本地部分，因此仅靠格式校验拦不住），且因为脱敏是幂等的，
 * 这种数据损坏在接口上不可见。
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NotMaskedValidator.class)
public @interface NotMasked {

    String message() default "请提交原始值, 接口返回的脱敏值(含 ***)不能回填";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
