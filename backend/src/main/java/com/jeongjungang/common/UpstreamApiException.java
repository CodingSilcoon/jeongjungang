package com.jeongjungang.common;

/** ODsay/카카오 등 외부 API 호출 실패. 클라이언트는 fallback 처리. */
public class UpstreamApiException extends RuntimeException {

    public UpstreamApiException(String message) {
        super(message);
    }

    public UpstreamApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
