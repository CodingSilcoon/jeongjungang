package com.jeongjungang.push;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * 서버가 보내는 FCM data 메시지 (docs/API.md 8절). 내용은 최소라서 자세한 건 받은 뒤 GET으로 가져온다.
 */
final class PushPayload {

    enum Type { ALARM_SYNC, ALARM_CANCELLED, ESCALATION }

    final Type type;
    final String meetingId;
    /** ESCALATION만. 안 일어난 사람의 알람 id. */
    final String alarmId;
    /** ESCALATION만. */
    final String sleeperNickname;
    /** ESCALATION만. 서버가 에스컬레이션한 시각(epoch ms). */
    final long escalatedAtMillis;

    private PushPayload(Type type, String meetingId, String alarmId, String sleeperNickname, long escalatedAtMillis) {
        this.type = type;
        this.meetingId = meetingId;
        this.alarmId = alarmId;
        this.sleeperNickname = sleeperNickname;
        this.escalatedAtMillis = escalatedAtMillis;
    }

    /** 모르는 종류이거나 필요한 값이 없으면 null. 앱보다 새 서버가 보낸 메시지는 무시한다. */
    static PushPayload parse(Map<String, String> data) {
        if (data == null) {
            return null;
        }
        Type type;
        try {
            type = Type.valueOf(String.valueOf(data.get("type")));
        } catch (IllegalArgumentException e) {
            return null;
        }
        String meetingId = nonBlank(data.get("meetingId"));
        if (meetingId == null) {
            return null;
        }
        if (type != Type.ESCALATION) {
            return new PushPayload(type, meetingId, null, null, 0);
        }
        String alarmId = nonBlank(data.get("alarmId"));
        String escalatedAt = nonBlank(data.get("escalatedAt"));
        if (alarmId == null || escalatedAt == null) {
            return null;
        }
        try {
            return new PushPayload(type, meetingId, alarmId, data.get("sleeperNickname"),
                    Instant.parse(escalatedAt).toEpochMilli());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String nonBlank(String s) {
        return s == null || s.trim().isEmpty() ? null : s;
    }
}
