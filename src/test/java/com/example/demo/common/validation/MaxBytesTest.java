package com.example.demo.common.validation;

import com.example.demo.model.dto.CreateUserRequest;
import com.example.demo.model.dto.UpdateProfileRequest;
import com.example.demo.model.dto.UpdateUserRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MaxBytes} 与密码约束：BCrypt 只处理前 72 字节，因此必须按字节而非字符校验。
 */
class MaxBytesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    record Probe(@MaxBytes(5) String value) {
    }

    @Test
    void countsUtf8BytesNotCharacters() {
        assertThat(validator.validate(new Probe("abcde"))).isEmpty();
        assertThat(validator.validate(new Probe("abc"))).isEmpty();
        assertThat(validator.validate(new Probe(null))).isEmpty();
        // 2 个中文字符 = 6 字节，超过 5 字节上限
        assertThat(validator.validate(new Probe("中文"))).singleElement()
                .satisfies(violation -> assertThat(violation.getMessage()).contains("5 字节"));
    }

    @Test
    void passwordLongerThanSeventyTwoBytesIsRejected() {
        // 25 个中文字符 = 75 字节：字符口径合规（>8 位），字节口径越界
        var tooManyBytes = "密".repeat(25);
        var request = new CreateUserRequest("alice", tooManyBytes, null, null);

        assertThat(validator.validate(request))
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("password");
                    assertThat(violation.getMessage()).contains("72 字节");
                });
    }

    @Test
    void multiBytePasswordWithinLimitIsAccepted() {
        // 20 个中文字符 = 60 字节，合规
        var request = new CreateUserRequest("alice", "密".repeat(20), null, null);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void shortPasswordIsRejectedOnMinimumLength() {
        var violation = validator.validate(new CreateUserRequest("alice", "short", null, null)).iterator().next();

        assertThat(violation.getPropertyPath().toString()).isEqualTo("password");
        assertThat(violation.getMessage()).contains("至少 8 位");
    }

    @Test
    void sameRulesApplyToUpdateRequests() {
        assertThat(validator.validate(new UpdateUserRequest(null, null, null, "密".repeat(25)))).hasSize(1);
        assertThat(validator.validate(new UpdateProfileRequest(null, null, "密".repeat(25)))).hasSize(1);
        assertThat(validator.validate(new UpdateProfileRequest(null, null, "password123"))).isEmpty();
    }
}
