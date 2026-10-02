package com.jeongjungang.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 약속 id ↔ 서버 알람 id. 기기 알람은 서버 알람 id로 예약하므로,
 * 알람이 바뀌거나 꺼졌을 때 이전 기기 알람을 지우고, 끈 기록을 보낼 때 어느 약속의 토큰을 쓸지 찾는 데 쓴다.
 */
final class AlarmLinks {

    private static final String PREFS = "alarm_links";

    private final SharedPreferences prefs;

    AlarmLinks(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized void put(String meetingId, String alarmId) {
        String old = alarmFor(meetingId);
        SharedPreferences.Editor e = prefs.edit();
        if (old != null && !old.equals(alarmId)) {
            e.remove("alarm." + old);
        }
        e.putString("meeting." + meetingId, alarmId).putString("alarm." + alarmId, meetingId).commit();
    }

    synchronized String alarmFor(String meetingId) {
        return prefs.getString("meeting." + meetingId, null);
    }

    synchronized String meetingFor(String alarmId) {
        return prefs.getString("alarm." + alarmId, null);
    }

    /** 약속 쪽 연결만 지운다. 끈 기록 전송에 쓰도록 알람 → 약속은 남긴다({@link #forgetAlarm}에서 지움). */
    synchronized void unlinkMeeting(String meetingId) {
        prefs.edit().remove("meeting." + meetingId).commit();
    }

    synchronized void forgetAlarm(String alarmId) {
        prefs.edit().remove("alarm." + alarmId).commit();
    }
}
