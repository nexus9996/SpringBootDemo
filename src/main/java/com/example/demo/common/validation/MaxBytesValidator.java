package com.example.demo.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

/**
 * {@link MaxBytes} 校验器：按 UTF-8 编码后的字节数比较，{@code null} 视为通过（是否必填交给其它注解）。
 */
public class MaxBytesValidator implements ConstraintValidator<MaxBytes, CharSequence> {

    private int maxBytes;

    @Override
    public void initialize(MaxBytes constraintAnnotation) {
        this.maxBytes = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value.toString().getBytes(StandardCharsets.UTF_8).length <= maxBytes;
    }
}
