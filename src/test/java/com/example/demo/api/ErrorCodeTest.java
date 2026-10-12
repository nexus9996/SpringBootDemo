package com.example.demo.api;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    void codesAreUnique() {
        List<Integer> codes = Arrays.stream(ErrorCode.values()).map(ErrorCode::code).toList();
        assertThat(codes).doesNotHaveDuplicates();
    }

    @Test
    void everyCodeIsFullyPopulated() {
        assertThat(ErrorCode.values()).allSatisfy(errorCode -> {
            assertThat(errorCode.message()).isNotBlank();
            assertThat(errorCode.httpStatus()).isNotNull();
            assertThat(errorCode.code()).isPositive();
        });
    }

    @Test
    void successAndCreatedMatchHttpSemantics() {
        assertThat(ErrorCode.SUCCESS.code()).isEqualTo(200);
        assertThat(ErrorCode.CREATED.code()).isEqualTo(201);
    }

    @Test
    void businessCodesAreGroupedByHttpStatus() {
        // 业务码 = HTTP 状态 * 100 + 序号，保证同一类错误聚集且可读
        assertThat(ErrorCode.VALIDATION_FAILED.code()).isEqualTo(40000);
        assertThat(ErrorCode.BAD_CREDENTIALS.code()).isEqualTo(40101);
        assertThat(ErrorCode.ORDER_STATUS_ILLEGAL.code()).isEqualTo(40902);
        assertThat(ErrorCode.INTERNAL_ERROR.code()).isEqualTo(50000);
    }
}
