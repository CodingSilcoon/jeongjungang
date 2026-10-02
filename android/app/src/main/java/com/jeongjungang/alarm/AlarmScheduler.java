package com.jeongjungang.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import com.jeongjungang.ui.MainActivity;
import java.util.List;

/**
 * 출발 알람 예약·취소. 화면과 (3단계) FCM 동기화가 쓰는 입구다.
 * {@link AlarmManager#setAlarmClock}을 써서 Doze에서도 정확한 시각에 울린다.
 */
public final class AlarmScheduler {

    public enum Result {
        SCHEDULED,
        /** 정확한 알람 권한이 없음. {@link AlarmPermissions#exactAlarmSettings}로 보낸다. */
        NEEDS_EXACT_ALARM_PERMISSION,
        /** 알람 시각이 이미 지남. */
        IN_PAST
    }

    private static final String TAG = "AlarmScheduler";

    private final Context context;
    private final AlarmStore store;

    public AlarmScheduler(Context context) {
        this.context = context.getApplicationContext();
        this.store = new AlarmStore(this.context);
    }

    /** 같은 id로 다시 부르면 시각·내용을 바꿔 다시 예약한다(약속 변경 시). */
    public Result schedule(AlarmSpec spec) {
        if (spec.fireAtMillis <= System.currentTimeMillis()) {
            return Result.IN_PAST;
        }
        if (!AlarmPermissions.canScheduleExactAlarms(context)) {
            return Result.NEEDS_EXACT_ALARM_PERMISSION;
        }
        store.put(spec);
        setSystemAlarm(spec);
        return Result.SCHEDULED;
    }

    public void cancel(String alarmId) {
        AlarmManager am = context.getSystemService(AlarmManager.class);
        PendingIntent pi = firePendingIntent(alarmId, PendingIntent.FLAG_NO_CREATE);
        if (am != null && pi != null) {
            am.cancel(pi);
            pi.cancel();
        }
        store.remove(alarmId);
    }

    /** 저장된 알람 (예약 시각은 지났지만 아직 끄지 않은 것 포함). */
    public List<AlarmSpec> scheduled() {
        return store.all();
    }

    public AlarmSpec find(String alarmId) {
        return store.get(alarmId);
    }

    /**
     * 재부팅, 시간대 변경, 정확한 알람 권한을 다시 받았을 때 저장된 알람을 다시 예약한다.
     * 이미 지난 알람은 지운다(꺼진 동안 지나간 알람을 뒤늦게 울리지 않는다).
     */
    void rescheduleAll() {
        if (store.isEmpty()) {
            return;
        }
        if (!AlarmPermissions.canScheduleExactAlarms(context)) {
            Log.w(TAG, "정확한 알람 권한이 없어 다시 예약하지 못함");
            return;
        }
        long now = System.currentTimeMillis();
        for (AlarmSpec spec : store.all()) {
            if (spec.fireAtMillis <= now) {
                store.remove(spec.id);
            } else {
                setSystemAlarm(spec);
            }
        }
    }

    /** 울린 뒤 끄거나 시간이 지나 멈췄을 때. 예약 목록에서 지운다. */
    void finished(String alarmId) {
        store.remove(alarmId);
    }

    private void setSystemAlarm(AlarmSpec spec) {
        AlarmManager am = context.getSystemService(AlarmManager.class);
        if (am == null) {
            return;
        }
        // 상태 표시줄 알람 아이콘을 누르면 앱을 연다
        PendingIntent show = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent fire = firePendingIntent(spec.id, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(spec.fireAtMillis, show), fire);
        } catch (SecurityException e) {
            // 확인과 예약 사이에 권한이 꺼진 경우
            Log.w(TAG, "정확한 알람 권한이 없어 예약 실패: " + spec.id, e);
        }
    }

    private PendingIntent firePendingIntent(String alarmId, int flags) {
        Intent intent = new Intent(context, AlarmFireReceiver.class)
                .setAction(AlarmFireReceiver.ACTION_FIRE)
                // id마다 다른 PendingIntent가 되도록 data에 넣는다
                .setData(Uri.parse("jeongjungang://alarm/" + Uri.encode(alarmId)))
                .putExtra(AlarmFireReceiver.EXTRA_ALARM_ID, alarmId);
        return PendingIntent.getBroadcast(context, 0, intent, flags | PendingIntent.FLAG_IMMUTABLE);
    }

}
