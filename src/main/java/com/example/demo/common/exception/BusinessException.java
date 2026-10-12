package com.example.demo.common.exception;

import com.example.demo.api.ErrorCode;

import java.util.Objects;

/**
 * 业务异常：可预期的业务规则拒绝，由 {@code GlobalExceptionHandler} 转换为对应 HTTP 状态与业务码。
 *
 * <p>只用于"用户可理解、可恢复"的失败；编程错误一律用 {@link IllegalArgumentException} /
 * {@link IllegalStateException} 等标准异常，交给兜底处理器返回 500。
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.message());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
