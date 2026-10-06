package com.jeongjungang.ratelimit;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 고정 창 카운터. 첫 요청에 창을 열고(PEXPIRE), 창 안의 횟수가 limit을 넘으면 막는다.
 * INCR과 만료 설정을 Lua로 한 번에 해서 서버가 여러 대여도 정확하게 센다.
 */
@Component
class RedisRateLimiter implements RateLimiter {

    private static final String KEY_PREFIX = "rl:";
    private static final RedisScript<List> SCRIPT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
              ttl = tonumber(ARGV[1])
            end
            return {count, ttl}
            """, List.class);
    private static final long MILLIS_PER_SECOND = 1000;

    private final StringRedisTemplate redis;

    RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Decision tryAcquire(String key, int limit, Duration window) {
        List<?> result = redis.execute(SCRIPT, List.of(KEY_PREFIX + key), String.valueOf(window.toMillis()));
        if (result == null || result.size() < 2) {
            throw new IllegalStateException("요청 제한 스크립트 결과가 비었습니다");
        }
        long count = ((Number) result.get(0)).longValue();
        if (count <= limit) {
            return Decision.allow();
        }
        long ttlMillis = ((Number) result.get(1)).longValue();
        return Decision.reject((ttlMillis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND);
    }
}
