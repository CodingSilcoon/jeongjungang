package com.jeongjungang.meeting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 만료 삭제를 주기적으로 돌린다. 서버가 여러 대여도 같은 행을 지우는 것뿐이라 안전하다.
 * 기한이 지난 뒤 삭제 전까지는 API가 410 MEETING_EXPIRED로 막고 있다.
 */
@Component
class MeetingRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(MeetingRetentionScheduler.class);

    private final MeetingRetention retention;

    MeetingRetentionScheduler(MeetingRetention retention) {
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${jeongjungang.retention-interval}",
            initialDelayString = "${jeongjungang.retention-interval}")
    void run() {
        try {
            retention.purgeExpired();
        } catch (DataAccessException e) {
            // 다음 주기에 다시 시도한다. 예외를 던지면 스케줄이 멈추지는 않지만 로그가 지저분해진다
            log.warn("만료 약속 삭제에 실패했습니다. 다음 주기에 다시 시도합니다", e);
        }
    }
}
