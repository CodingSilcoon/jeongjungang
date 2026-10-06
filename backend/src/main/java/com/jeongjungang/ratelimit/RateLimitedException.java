package com.jeongjungang.ratelimit;

import com.jeongjungang.common.ApiException;
import com.jeongjungang.common.ErrorCode;

/** 429 RATE_LIMITED. 응답에 Retry-After(초) 헤더를 붙인다. */
public class RateLimitedException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
