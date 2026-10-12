package com.example.demo.api;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码。
 *
 * <p>{@link #code()} 是对外契约的一部分（业务码），{@link #httpStatus()} 决定真实 HTTP 响应状态。
 * 业务码按 {@code HTTP 状态 * 100 + 序号} 编排，便于归类与排查。
 */
public enum ErrorCode {

    SUCCESS(200, "成功", HttpStatus.OK),
    CREATED(201, "创建成功", HttpStatus.CREATED),

    VALIDATION_FAILED(40000, "参数校验失败", HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(40001, "请求体格式错误", HttpStatus.BAD_REQUEST),
    MISSING_PARAMETER(40002, "缺少必要参数", HttpStatus.BAD_REQUEST),
    PARAMETER_TYPE_MISMATCH(40003, "参数类型不匹配", HttpStatus.BAD_REQUEST),

    UNAUTHORIZED(40100, "未认证或凭证已失效", HttpStatus.UNAUTHORIZED),
    BAD_CREDENTIALS(40101, "用户名或密码错误", HttpStatus.UNAUTHORIZED),

    FORBIDDEN(40300, "无权访问该资源", HttpStatus.FORBIDDEN),

    RESOURCE_NOT_FOUND(40400, "资源不存在", HttpStatus.NOT_FOUND),

    METHOD_NOT_ALLOWED(40500, "不支持的请求方法", HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE(41500, "不支持的请求内容类型", HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    USERNAME_ALREADY_EXISTS(40901, "用户名已存在", HttpStatus.CONFLICT),
    ORDER_STATUS_ILLEGAL(40902, "订单状态不允许该操作", HttpStatus.CONFLICT),
    DATA_INTEGRITY_VIOLATION(40903, "数据完整性冲突", HttpStatus.CONFLICT),
    OPTIMISTIC_LOCK_CONFLICT(40904, "数据已被并发修改，请重试", HttpStatus.CONFLICT),

    INTERNAL_ERROR(50000, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
