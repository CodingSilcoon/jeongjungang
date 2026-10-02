package com.jeongjungang.alarm;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 재부팅·시간 변경·앱 업데이트·정확한 알람 권한 변경 때 저장된 알람을 다시 예약한다.
 *
 * <p>공식 가이드는 알람이 있을 때만 수신기를 켜라고 하지만, 켜고 끈 상태는 PackageManager가 늦게 저장해서
 * 예약 직후 재부팅하면 꺼진 상태로 남아 알람이 사라졌다(에뮬레이터에서 재현). 그래서 항상 켜 두고,
 * 저장된 알람이 없으면 아무것도 하지 않는다.
 */
public class AlarmRescheduleReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)) {
            new AlarmScheduler(context).rescheduleAll();
        }
    }
}
