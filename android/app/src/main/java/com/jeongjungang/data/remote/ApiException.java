package com.jeongjungang.data.remote;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
    /**
     * VALIDATION_FAILED일 때 서버가 준 필드별 사유(필드 이름 → 사용자용 문장). 예: "meetAt" → "약속 시간은 …".
     * 화면이 해당 입력칸 아래에 띄울 때 쓴다. 없으면 빈 맵.
     */
    public final Map<String, String> fields;

    public ApiException(String code, int httpStatus, String userMessage, int retryAfterSeconds, Throwable cause) {
        this(code, httpStatus, userMessage, retryAfterSeconds, cause, Collections.<String, String>emptyMap());
    }

    public ApiException(String code, int httpStatus, String userMessage, int retryAfterSeconds, Throwable cause,
                        Map<String, String> fields) {
        super(userMessage, cause);
        this.code = code;
        this.httpStatus = httpStatus;
        this.retryAfterSeconds = retryAfterSeconds;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<String, String>(fields));
    }

    public boolean isNetwork() {
        return NETWORK.equals(code);
    }
}
