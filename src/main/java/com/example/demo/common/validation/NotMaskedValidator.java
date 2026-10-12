package com.example.demo.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link NotMasked} 校验器：含连续三个星号即视为脱敏值，{@code null} 通过。
 */
public class NotMaskedValidator implements ConstraintValidator<NotMasked, String> {

    private static final String MASK_MARKER = "***";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || !value.contains(MASK_MARKER);
    }
}
