package com.jeongjungang.alarm;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 에스컬레이션 확인 예정 목록. Redis ZSET {@code escalation:due}, score = graceUntil(epoch ms), member = alarmId.
 * 기한이 된 항목은 Lua로 조회와 삭제를 한 번에 해서, 서버가 여러 대여도 한 항목은 한 서버만 가져간다.
 * 이 큐는 "언제 볼지"만 알려 준다. 보낼지 말지는 항상 DB 상태로 정한다.
 */
@Component
class EscalationQueue {

    static final String KEY = "escalation:due";
    private static final RedisScript<List> POP_DUE = RedisScript.of("""
            local items = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2])
            if #items > 0 then
              redis.call('ZREM', KEYS[1], unpack(items))
            end
            return items
            """, List.class);

    /** ZADD LT: 없으면 넣고, 있으면 더 이른 기한만 남긴다. */
    private static final RedisScript<Long> ADD_EARLIER = RedisScript.of(
            "return redis.call('ZADD', KEYS[1], 'LT', ARGV[1], ARGV[2])", Long.class);

    private final StringRedisTemplate redis;

    EscalationQueue(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 항목을 넣는다. 이미 있으면 더 이른 기한을 남긴다. 늦은 쪽을 남기면 오래된 값이 새 기한을 덮어 에스컬레이션이 늦을 수 있고,
     * 이른 쪽은 너무 일찍 꺼내도 {@link EscalationService#escalate}가 DB 기한으로 다시 넣으니 안전하다.
     * 끔·취소된 알람도 빼지 않는다. 꺼냈을 때 DB 상태를 보고 그냥 버린다(빼는 작업이 뒤늦게 돌아 새 항목을 지우는 경쟁을 없앤다).
     */
    void add(UUID alarmId, Instant graceUntil) {
        redis.execute(ADD_EARLIER, List.of(KEY), String.valueOf(graceUntil.toEpochMilli()), alarmId.toString());
    }

    void addAll(Collection<DueAlarm> entries) {
        entries.forEach(e -> add(e.alarmId(), e.graceUntil()));
    }

    /** 기한이 now 이하인 항목을 최대 limit개 꺼낸다(꺼낸 항목은 큐에서 빠진다). */
    List<UUID> popDue(Instant now, int limit) {
        List<?> items = redis.execute(POP_DUE, List.of(KEY),
                String.valueOf(now.toEpochMilli()), String.valueOf(limit));
        if (items == null) {
            return List.of();
        }
        return items.stream().map(Object::toString).map(UUID::fromString).toList();
    }
}
