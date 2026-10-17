package com.jeongjungang.alarm.api;

import com.jeongjungang.alarm.AlarmStatus;
import com.jeongjungang.meeting.api.MeetingResponses.AccountabilityView;
import java.time.Instant;
import java.util.UUID;

/** 책임 알람 응답 본문 (docs/API.md 7절). 시각은 UTC ISO 8601로 나간다. */
public final class AlarmResponses {

    private AlarmResponses() {
    }

    public record AlarmView(UUID id, Instant fireAt, Instant graceUntil, AlarmStatus status) {
    }

    public record MyAlarm(AccountabilityView accountability, AlarmView alarm, Instant serverTime) {
    }

    public record DismissResult(AlarmStatus status) {
    }
}
