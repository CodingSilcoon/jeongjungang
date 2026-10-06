package com.jeongjungang.ratelimit;

import java.time.Duration;

/** 키별 요청 횟수 제한. 창(window) 안에서 limit번까지 허용한다. */
@FunctionalInterface
public interface RateLimiter {

    Decision tryAcquire(String key, int limit, Duration window);

    /** retryAfterSeconds는 막혔을 때 창이 끝나기까지 남은 초(1 이상). */
    record Decision(boolean allowed, long retryAfterSeconds) {

        public static Decision allow() {
            return new Decision(true, 0);
        }

        public static Decision reject(long retryAfterSeconds) {
            return new Decision(false, Math.max(1, retryAfterSeconds));
        }
    }
}
