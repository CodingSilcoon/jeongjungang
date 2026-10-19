package com.jeongjungang.alarm;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 에스컬레이션 큐를 주기적으로 본다(기본 1초). 기한이 된 항목을 꺼내 하나씩 처리하고,
 * 처리에 실패한 항목은 바로 다시 넣어 다음 주기에 재시도한다.
 */
@Component
class EscalationScheduler {

    private static final Logger log = LoggerFactory.getLogger(EscalationScheduler.class);
    /** 한 주기에 꺼내는 최대 개수. 남은 건 다음 주기에 처리한다. */
    static final int BATCH_SIZE = 100;

    private final EscalationQueue queue;
    private final EscalationService escalation;
    private final Clock clock;

    EscalationScheduler(EscalationQueue queue, EscalationService escalation, Clock clock) {
        this.queue = queue;
        this.escalation = escalation;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${jeongjungang.escalation.poll-interval}")
    void poll() {
        List<UUID> due;
        try {
            due = queue.popDue(clock.instant(), BATCH_SIZE);
        } catch (DataAccessException e) {
            log.warn("에스컬레이션 큐를 읽지 못했습니다. 다음 주기에 다시 시도합니다", e);
            return;
        }
        due.forEach(this::process);
    }

    @Scheduled(fixedDelayString = "${jeongjungang.escalation.reseed-interval}",
            initialDelayString = "${jeongjungang.escalation.reseed-initial-delay}")
    void reseed() {
        try {
            escalation.reseedQueue();
        } catch (DataAccessException e) {
            log.warn("에스컬레이션 큐를 DB 기준으로 채우지 못했습니다. 다음 주기에 다시 시도합니다", e);
        }
    }

    private void process(UUID alarmId) {
        try {
            escalation.escalate(alarmId);
        } catch (RuntimeException e) {
            log.warn("알람 {} 에스컬레이션 처리에 실패했습니다. 다시 넣습니다", alarmId, e);
            requeue(alarmId);
        }
    }

    private void requeue(UUID alarmId) {
        try {
            queue.add(alarmId, clock.instant());
        } catch (DataAccessException e) {
            // 큐에 못 넣어도 다음 reseed가 DB에서 다시 채운다
            log.warn("알람 {}을 큐에 다시 넣지 못했습니다", alarmId, e);
        }
    }
}
