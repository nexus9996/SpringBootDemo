package com.example.demo.common.exception;

import com.example.demo.api.ApiResponse;
import com.example.demo.api.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器：把异常翻译成「统一响应包体 + 正确的 HTTP 状态码」。
 *
 * <p>分层约定：
 * <ul>
 *   <li>业务可预期失败 → {@link BusinessException} 及其子类，按 {@link ErrorCode#httpStatus()} 返回；</li>
 *   <li>入参错误 → 400，附带字段级明细；</li>
 *   <li>并发/约束冲突 → 409；</li>
 *   <li>未知异常 → 500，只记录日志不回显内部细节；</li>
 *   <li>认证/授权失败 → 交给 Spring Security 的 {@code ExceptionTranslationFilter}（见 {@code SecurityConfig}），
 *       保证与过滤器链的 EntryPoint/AccessDeniedHandler 行为一致。</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        log.debug("业务规则拒绝请求: code={}, message={}", e.errorCode().code(), e.getMessage());
        return respond(e.errorCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException e) {
        var message = e.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::describe)
                .collect(Collectors.joining("; "));
        return respond(ErrorCode.VALIDATION_FAILED, message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleParameterValidation(HandlerMethodValidationException e) {
        var message = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .map(GlobalExceptionHandler::describe)
                .collect(Collectors.joining("; "));
        return respond(ErrorCode.VALIDATION_FAILED, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.debug("请求体无法解析", e);
        return respond(ErrorCode.MALFORMED_REQUEST, ErrorCode.MALFORMED_REQUEST.message());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        return respond(ErrorCode.MISSING_PARAMETER, "缺少参数: " + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        var required = e.getRequiredType();
        return respond(ErrorCode.PARAMETER_TYPE_MISMATCH,
                "参数 '%s' 类型不正确, 期望: %s".formatted(e.getName(), required == null ? "未知" : required.getSimpleName()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return respond(ErrorCode.RESOURCE_NOT_FOUND, "路径不存在: " + e.getResourcePath());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, "不支持的请求方法: " + e.getMethod());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "不支持的 Content-Type: " + e.getContentType());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException e) {
        log.warn("并发修改冲突: {}", e.getMessage());
        return respond(ErrorCode.OPTIMISTIC_LOCK_CONFLICT, ErrorCode.OPTIMISTIC_LOCK_CONFLICT.message());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("数据完整性冲突", e);
        return respond(ErrorCode.DATA_INTEGRITY_VIOLATION, ErrorCode.DATA_INTEGRITY_VIOLATION.message());
    }

    /**
     * 认证/授权异常交给 Spring Security 处理：匿名 → 401，已认证但无权限 → 403。
     *
     * <p>若在此处消费掉 {@link AccessDeniedException}，过滤器链的 {@code ExceptionTranslationFilter}
     * 就再也拿不到该异常，认证失败会被错误地渲染成授权失败。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(AccessDeniedException e) throws AccessDeniedException {
        throw e;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        log.error("未预期的服务端异常", e);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.message());
    }

    private static String describe(FieldError fieldError) {
        return "%s: %s".formatted(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private static String describe(MessageSourceResolvable resolvable) {
        var message = resolvable.getDefaultMessage();
        return message == null ? "参数不合法" : message;
    }

    private static ResponseEntity<ApiResponse<Void>> respond(ErrorCode errorCode, String message) {
        ApiResponse<Void> body = message == null || message.isBlank()
                ? ApiResponse.fail(errorCode)
                : ApiResponse.fail(errorCode, message);
        return ResponseEntity.status(errorCode.httpStatus()).body(body);
    }
}
