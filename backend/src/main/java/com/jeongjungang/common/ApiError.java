package com.jeongjungang.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** 응답의 error 부분. fields는 VALIDATION_FAILED일 때만 채운다(필드명 -> 사유). */
public record ApiError(
        String code,
        String message,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> fields) {

    public static ApiError of(ErrorCode code) {
        return new ApiError(code.name(), code.message(), Map.of());
    }

    public static ApiError of(ErrorCode code, Map<String, String> fields) {
        return new ApiError(code.name(), code.message(), Map.copyOf(fields));
    }
}
