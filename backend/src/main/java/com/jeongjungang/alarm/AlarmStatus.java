package com.jeongjungang.alarm;

/**
 * 알람 상태 (docs/API.md 7절 "알람 상태").
 * SCHEDULED → RINGING → DISMISSED, 유예시간까지 끔 보고가 없으면 ESCALATED, 끄거나 나가면 CANCELLED.
 */
public enum AlarmStatus {
    SCHEDULED, RINGING, DISMISSED, ESCALATED, CANCELLED;

    /** 아직 끔 보고를 기다리는 중. 이 상태만 에스컬레이션 대상이다. */
    public boolean isActive() {
        return this == SCHEDULED || this == RINGING;
    }
}
