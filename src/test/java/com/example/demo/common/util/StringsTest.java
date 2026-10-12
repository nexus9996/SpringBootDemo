package com.example.demo.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class StringsTest {

    @ParameterizedTest
    @ValueSource(strings = {"a", " a ", "0"})
    void hasTextTrueForNonBlank(String value) {
        assertThat(Strings.hasText(value)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n", "   "})
    void hasTextFalseForNullAndBlank(String value) {
        assertThat(Strings.hasText(value)).isFalse();
    }

    @Test
    void defaultIfBlankFallsBackWhenBlank() {
        assertThat(Strings.defaultIfBlank(null, "fallback")).isEqualTo("fallback");
        assertThat(Strings.defaultIfBlank("  ", "fallback")).isEqualTo("fallback");
        assertThat(Strings.defaultIfBlank("kept", "fallback")).isEqualTo("kept");
    }

    @ParameterizedTest
    @CsvSource({
        "13812345678, 138****5678",
        "13800000000, 138****0000",
        "19999999999, 199****9999",
        "+8613812345678, +86138****5678",
        "8613812345678, 86138****5678"
    })
    void maskPhoneKeepsPrefixAndSuffix(String raw, String masked) {
        assertThat(Strings.maskPhone(raw)).isEqualTo(masked);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "1381234567", "138123456789", "not-a-phone"})
    void maskPhoneLeavesNonMobileUntouched(String raw) {
        assertThat(Strings.maskPhone(raw)).isEqualTo(raw);
    }

    @Test
    void maskPhoneKeepsNull() {
        assertThat(Strings.maskPhone(null)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
        "alice@example.com, a***e@example.com",
        "ab@example.com, a***@example.com",
        "a@example.com, a***@example.com"
    })
    void maskEmailKeepsFirstAndLastLocalChar(String raw, String masked) {
        assertThat(Strings.maskEmail(raw)).isEqualTo(masked);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "trailing@"})
    void maskEmailLeavesInvalidUntouched(String raw) {
        assertThat(Strings.maskEmail(raw)).isEqualTo(raw);
    }

    @Test
    void maskEmailKeepsNull() {
        assertThat(Strings.maskEmail(null)).isNull();
    }
}
