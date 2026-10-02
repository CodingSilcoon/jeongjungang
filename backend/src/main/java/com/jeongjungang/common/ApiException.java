package com.jeongjungang.common;

/**
 * 오류 코드로 응답할 도메인 예외.
 * logDetail은 서버 로그에만 남고 응답에는 나가지 않는다.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public ApiException(ErrorCode code, String logDetail, Throwable cause) {
        super(code.name() + ": " + logDetail, cause);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
