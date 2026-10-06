package com.jeongjungang.alarm;

/** 기기에 예약할 출발 알람 한 건. 3단계에서는 서버의 Alarm(id, fireAt)과 1:1로 맞춘다. */
public final class AlarmSpec {

    /** 알람 id. 서버 연동 후에는 서버 alarmId를 쓴다. */
    public final String id;
    public final long fireAtMillis;
    /** 알람 화면 제목. 예: "민수 생일 모임" */
    public final String title;
    /** 약속 장소 표시 이름. 예: "답십리역". 없으면 null */
    public final String place;
    /** 약속 시각. 화면에 "19:00 약속"처럼 보여 준다. 모르면 0 */
    public final long meetAtMillis;

    public AlarmSpec(String id, long fireAtMillis, String title, String place, long meetAtMillis) {
        if (id == null || id.isEmpty() || title == null) {
            throw new IllegalArgumentException("알람 id와 제목이 필요합니다.");
        }
        this.id = id;
        this.fireAtMillis = fireAtMillis;
        this.title = title;
        this.place = place;
        this.meetAtMillis = meetAtMillis;
    }
}
