package com.jeongjungang.cache;

import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 역-역 소요시간(분) Redis 캐시. */
@Component
public class TravelTimeCache {

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public TravelTimeCache(StringRedisTemplate redis,
                           @Value("${cache.travel-time-ttl-hours}") long ttlHours) {
        this.redis = redis;
        this.ttl = Duration.ofHours(ttlHours);
    }

    public Optional<Integer> get(StationPairKey key) {
        String value = redis.opsForValue().get(key.toRedisKey());
        return value == null ? Optional.empty() : Optional.of(Integer.parseInt(value));
    }

    public void put(StationPairKey key, int minutes) {
        redis.opsForValue().set(key.toRedisKey(), Integer.toString(minutes), ttl);
    }
}
