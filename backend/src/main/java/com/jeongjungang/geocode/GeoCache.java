package com.jeongjungang.geocode;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 카카오 결과를 Redis에 JSON으로 캐시한다(쿼터 절약). 성공한 결과만 저장한다.
 * Redis가 안 되면 캐시 없이 카카오를 바로 부른다. 캐시 때문에 검색이 실패하면 안 되기 때문이다.
 */
@Component
class GeoCache {

    private static final Logger log = LoggerFactory.getLogger(GeoCache.class);
    private static final String KEY_PREFIX = "geo:";

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    GeoCache(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        Optional<T> cached = read(KEY_PREFIX + key, type);
        if (cached.isPresent()) {
            return cached.get();
        }
        T value = loader.get();
        write(KEY_PREFIX + key, value, ttl);
        return value;
    }

    private <T> Optional<T> read(String key, Class<T> type) {
        try {
            String raw = redis.opsForValue().get(key);
            return raw == null ? Optional.empty() : Optional.of(json.readValue(raw, type));
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("주소 캐시를 읽지 못해 카카오를 바로 부릅니다: {}", e.toString());
            return Optional.empty();
        }
    }

    private void write(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, json.writeValueAsString(value), ttl);
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("주소 캐시에 저장하지 못했습니다: {}", e.toString());
        }
    }
}
