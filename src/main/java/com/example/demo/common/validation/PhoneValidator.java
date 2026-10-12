package com.example.demo.common.validation;

import com.example.demo.common.util.Strings;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 手机号校验器：允许 {@code null}（是否必填交给 {@code @NotBlank}），支持 {@code +86} / {@code 86} 前缀。
 */
public class PhoneValidator implements ConstraintValidator<ValidPhone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || Strings.isMobilePhone(value);
    }
}
