package com.jeongjungang.common;

import java.util.Map;

/**
 * 오류 코드로 응답할 도메인 예외.
 * logDetail은 서버 로그에만 남고 응답에는 나가지 않는다. fields는 VALIDATION_FAILED의 필드별 사유다.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, String> fields;

    public ApiException(ErrorCode code) {
        this(code, Map.of());
    }

    public ApiException(ErrorCode code, Map<String, String> fields) {
        super(code.name());
        this.code = code;
        this.fields = Map.copyOf(fields);
    }

    public ApiException(ErrorCode code, String logDetail, Throwable cause) {
        super(code.name() + ": " + logDetail, cause);
        this.code = code;
        this.fields = Map.of();
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, String> fields() {
        return fields;
    }
}
