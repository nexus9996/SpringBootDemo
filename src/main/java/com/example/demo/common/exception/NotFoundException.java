package com.example.demo.common.exception;

import com.example.demo.api.ErrorCode;

/**
 * 资源不存在：{@link ErrorCode#RESOURCE_NOT_FOUND}，HTTP 404。
 */
public class NotFoundException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String resource, Object id) {
        super(ErrorCode.RESOURCE_NOT_FOUND, "%s(id=%s) 不存在".formatted(resource, id));
    }

    public NotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
