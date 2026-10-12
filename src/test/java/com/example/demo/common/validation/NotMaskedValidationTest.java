package com.example.demo.common.validation;

import com.example.demo.model.dto.UpdateProfileRequest;
import com.example.demo.model.dto.UpdateUserRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * 脱敏值回填防护：接口返回的 {@code 138****5678} / {@code a***e@example.com} 不能被当作真实值写库。
 * {@code @Email} 允许本地部分出现 {@code *}，因此必须由独立约束拦截。
 */
class NotMaskedValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    record Probe(@NotMasked String value) {
    }

    @Test
    void rejectsValuesContainingMaskMarker() {
        assertThat(validator.validate(new Probe("a***e@example.com")))
                .singleElement()
                .satisfies(violation -> assertThat(violation.getMessage()).contains("脱敏"));
        assertThat(validator.validate(new Probe("138****5678"))).hasSize(1);
        assertThat(validator.validate(new Probe("alice@example.com"))).isEmpty();
        assertThat(validator.validate(new Probe("13812345678"))).isEmpty();
        assertThat(validator.validate(new Probe(null))).isEmpty();
    }

    @Test
    void maskedEmailIsValidForEmailButRejectedOverall() {
        // 单看 @Email 是合法的，因此这个用例证明"仅靠格式校验拦不住回填"
        assertThat(validator.validate(new UpdateProfileRequest("a***e@example.com", null, null)))
                .singleElement()
                .satisfies(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("email"));

        assertThat(validator.validate(new UpdateProfileRequest("alice@example.com", "13812345678", null))).isEmpty();
    }

    @Test
    void adminUpdatePathIsProtectedToo() {
        var violations = validator.validate(new UpdateUserRequest("a***e@example.com", "138****5678", null, null));

        assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
                .contains("email", "phone");
        assertThat(violations).anySatisfy(violation -> assertThat(violation.getMessage()).contains("脱敏"));
        assertThat(violations).anySatisfy(violation -> assertThat(violation.getMessage()).contains("手机号"));

        var paths = validator.validate(new UpdateUserRequest(null, "138****5678", null, null)).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .toList();
        assertThat(paths).isNotEmpty().containsOnly("phone");
    }
}
