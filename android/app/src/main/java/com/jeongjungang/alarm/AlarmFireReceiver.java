package com.jeongjungang.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;

/** AlarmManager가 알람 시각에 보내는 브로드캐스트. 울림 서비스를 포그라운드로 시작한다. */
public class AlarmFireReceiver extends BroadcastReceiver {

    static final String ACTION_FIRE = "com.jeongjungang.alarm.FIRE";
    static final String EXTRA_ALARM_ID = "alarmId";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_FIRE.equals(intent.getAction())) {
            return;
        }
        String alarmId = intent.getStringExtra(EXTRA_ALARM_ID);
        if (alarmId == null) {
            return;
        }
        // setAlarmClock 알람이 보낸 브로드캐스트에서는 백그라운드에서도 포그라운드 서비스를 시작할 수 있다
        ContextCompat.startForegroundService(context, AlarmRingService.ringIntent(context, alarmId));
    }
}
