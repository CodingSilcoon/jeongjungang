package com.jeongjungang.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 모든 오류를 docs/API.md 3절 형식으로 바꾼다. 응답에는 내부 정보를 넣지 않고, 자세한 내용은 로그에만 남긴다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INVALID_VALUE = "올바르지 않은 값입니다.";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException e) {
        if (e.code().status().is5xxServerError()) {
            log.warn("API 오류: {}", e.getMessage(), e);
        }
        return respond(ApiError.of(e.code()), e.code());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidBody(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), messageOrDefault(error.getDefaultMessage()));
        }
        return validationFailed(fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidParams(HandlerMethodValidationException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            String message = result.getResolvableErrors().isEmpty()
                    ? INVALID_VALUE
                    : messageOrDefault(result.getResolvableErrors().get(0).getDefaultMessage());
            fields.putIfAbsent(name == null ? "request" : name, message);
        });
        return validationFailed(fields);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return validationFailed(Map.of(e.getParameterName(), "필수 값입니다."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return validationFailed(Map.of(e.getName(), INVALID_VALUE));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        return validationFailed(Map.of("body", "요청 본문을 읽을 수 없습니다."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return respond(ApiError.of(ErrorCode.NOT_FOUND), ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException e) {
        return respond(ApiError.of(ErrorCode.METHOD_NOT_ALLOWED), ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return respond(ApiError.of(ErrorCode.INTERNAL_ERROR), ErrorCode.INTERNAL_ERROR);
    }

    private static ResponseEntity<ApiResponse<Void>> validationFailed(Map<String, String> fields) {
        return respond(ApiError.of(ErrorCode.VALIDATION_FAILED, fields), ErrorCode.VALIDATION_FAILED);
    }

    private static ResponseEntity<ApiResponse<Void>> respond(ApiError error, ErrorCode code) {
        return ResponseEntity.status(code.status()).body(ApiResponse.fail(error));
    }

    private static String messageOrDefault(String message) {
        return message == null || message.isBlank() ? INVALID_VALUE : message;
    }
}
