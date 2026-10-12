package com.example.demo.api;

/**
 * 统一响应包体。
 *
 * <p>sealed 保证只有成功/失败两种形态；分页等结构化数据作为 {@code T} 负载（见 {@link PageResponse}）承载。
 * HTTP 状态码由 {@link ErrorCode#httpStatus()} 决定，业务码见 {@link #code()}。
 */
public sealed interface ApiResponse<T> permits ApiResponse.Success, ApiResponse.Failure {

    record Success<T>(int code, String message, T data, long timestamp) implements ApiResponse<T> {
    }

    record Failure<T>(int code, String message, long timestamp) implements ApiResponse<T> {
    }

    int code();

    String message();

    long timestamp();

    static <T> ApiResponse<T> ok(T data) {
        return new Success<>(ErrorCode.SUCCESS.code(), ErrorCode.SUCCESS.message(), data, now());
    }

    static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    static <T> ApiResponse<T> created(T data) {
        return new Success<>(ErrorCode.CREATED.code(), ErrorCode.CREATED.message(), data, now());
    }

    static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.message());
    }

    static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new Failure<>(errorCode.code(), message, now());
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
