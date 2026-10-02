package com.jeongjungang.alarm;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 알람을 끈 기록. 서버(`POST /alarms/{id}/dismiss`)에 보고할 때까지 기기에 쌓아 둔다.
 * 3단계 서버 연동 전에는 쌓기만 한다. 연동 후에는 네트워크가 돌아오면 {@link #pending()}을 보내고
 * 성공한 것만 {@link #remove(String)} 한다 (CLAUDE.md: dismiss 보고 실패 시 로컬 큐에 쌓았다가 재전송).
 */
public final class DismissQueue {

    private static final String PREFS = "alarm_dismiss_queue";

    private final SharedPreferences prefs;

    public DismissQueue(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 같은 알람을 여러 번 끄면 처음 끈 시각을 남긴다(서버 dismiss가 멱등이라 한 번이면 된다). */
    public synchronized void add(String alarmId, long dismissedAtMillis) {
        if (!prefs.contains(alarmId)) {
            prefs.edit().putLong(alarmId, dismissedAtMillis).commit();
        }
    }

    /** alarmId → 끈 시각(epoch ms). */
    public synchronized Map<String, Long> pending() {
        Map<String, Long> out = new LinkedHashMap<String, Long>();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            if (e.getValue() instanceof Long) {
                out.put(e.getKey(), (Long) e.getValue());
            }
        }
        return out;
    }

    public synchronized void remove(String alarmId) {
        prefs.edit().remove(alarmId).commit();
    }
}
