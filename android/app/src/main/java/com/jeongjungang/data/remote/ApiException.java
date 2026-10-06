package com.jeongjungang.data.remote;

/**
 * 서버 호출 실패. {@link #getMessage()}는 사용자에게 그대로 보여도 되는 문장이다
 * (서버가 준 error.message이거나 앱이 정한 문장).
 */
public final class ApiException extends Exception {

    /** 서버에 닿지 못함(오프라인, 시간 초과 등). */
    public static final String NETWORK = "NETWORK";
    /** 응답을 해석하지 못함. */
    public static final String BAD_RESPONSE = "BAD_RESPONSE";
    public static final String RATE_LIMITED = "RATE_LIMITED";

    /** docs/API.md 오류 코드 또는 위 상수. */
    public final String code;
    /** HTTP 상태. 서버에 닿지 못했으면 0. */
    public final int httpStatus;
    /** 429일 때 Retry-After(초). 없으면 0. */
    public final int retryAfterSeconds;

    public ApiException(String code, int httpStatus, String userMessage, int retryAfterSeconds, Throwable cause) {
        super(userMessage, cause);
        this.code = code;
        this.httpStatus = httpStatus;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public boolean isNetwork() {
        return NETWORK.equals(code);
    }
}
