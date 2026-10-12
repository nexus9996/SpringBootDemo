package com.example.demo.common.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    record Probe(@ValidPhone String phone) {
    }

    @ParameterizedTest
    @ValueSource(strings = {"13812345678", "19912345678", "15000000000", "+8613812345678", "8613812345678"})
    void acceptsMainlandMobileNumbersIncludingCountryCode(String phone) {
        assertThat(validator.validate(new Probe(phone))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12812345678", "1381234567", "138123456789", "1381234567a", "1a812345678"})
    void rejectsMalformedNumbers(String phone) {
        assertThat(validator.validate(new Probe(phone)))
                .singleElement()
                .satisfies(violation -> assertThat(violation.getMessage()).isEqualTo("手机号格式不正确"));
    }

    @Test
    void nullIsLeftToNotBlank() {
        assertThat(validator.validate(new Probe(null))).isEmpty();
    }
}
