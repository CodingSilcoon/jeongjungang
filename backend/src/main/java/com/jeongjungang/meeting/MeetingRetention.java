package com.jeongjungang.meeting;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기한이 지난 약속을 지운다 (docs/API.md 4절: 약속 시각 + 24시간, 시각이 없으면 생성 + 7일).
 * 참가자(출발지·별명)는 외래키 ON DELETE CASCADE로 함께 지워진다.
 * 같은 약속에 참가 중인 요청이 있으면 그 요청이 약속 행 잠금을 놓을 때까지 기다렸다가 지운다.
 */
@Service
public class MeetingRetention {

    private static final Logger log = LoggerFactory.getLogger(MeetingRetention.class);

    private final MeetingRepository meetings;
    private final Clock clock;

    MeetingRetention(MeetingRepository meetings, Clock clock) {
        this.meetings = meetings;
        this.clock = clock;
    }

    @Transactional
    public int purgeExpired() {
        int deleted = meetings.deleteExpired(clock.instant());
        if (deleted > 0) {
            log.info("기한이 지난 약속 {}개를 지웠습니다", deleted);
        }
        return deleted;
    }
}
