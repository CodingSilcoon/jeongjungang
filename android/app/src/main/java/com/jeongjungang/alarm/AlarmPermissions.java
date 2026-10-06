package com.jeongjungang.alarm;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * 알람이 제대로 울리는 데 필요한 권한·설정 확인과 설정 화면 이동. 온보딩이나 알람 켜기 화면에서 쓴다.
 *
 * <ul>
 *   <li>정확한 알람(SCHEDULE_EXACT_ALARM): Android 14부터 새로 설치하면 꺼져 있다. 없으면 예약 자체가 안 된다</li>
 *   <li>전체 화면 알림(USE_FULL_SCREEN_INTENT): Android 14부터 알람·전화 앱 외에는 꺼져 있을 수 있다.
 *       없으면 잠금 화면에 알람 화면 대신 알림만 뜬다(소리는 난다)</li>
 *   <li>알림(POST_NOTIFICATIONS): Android 13부터 런타임 권한. 없으면 알림이 안 보인다</li>
 *   <li>배터리 최적화 제외: 필수는 아니지만 삼성·샤오미에서 알람이 밀리는 것을 줄인다</li>
 * </ul>
 */
public final class AlarmPermissions {

    private AlarmPermissions() {}

    public static boolean canScheduleExactAlarms(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        AlarmManager am = context.getSystemService(AlarmManager.class);
        return am != null && am.canScheduleExactAlarms();
    }

    public static boolean canUseFullScreenIntent(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return true;
        }
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        return nm != null && nm.canUseFullScreenIntent();
    }

    /** Android 13 미만은 항상 true. 13 이상에서 false면 화면이 POST_NOTIFICATIONS 권한을 요청한다. */
    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isIgnoringBatteryOptimizations(Context context) {
        PowerManager pm = context.getSystemService(PowerManager.class);
        return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    /** 필수 항목(정확한 알람, 알림)이 모두 있으면 true. */
    public static boolean isReady(Context context) {
        return canScheduleExactAlarms(context) && hasNotificationPermission(context);
    }

    /** "알람 및 리마인더" 설정 화면. Android 12 미만은 null. */
    public static Intent exactAlarmSettings(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return null;
        }
        return new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context));
    }

    /** 전체 화면 알림 설정 화면. Android 14 미만은 null. */
    public static Intent fullScreenIntentSettings(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return null;
        }
        return new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri(context));
    }

    /** 앱 알림 설정 화면 (권한 요청을 사용자가 두 번 거절한 뒤에 쓴다). */
    public static Intent notificationSettings(Context context) {
        return new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
    }

    /**
     * 배터리 최적화 앱 목록 화면. 사용자가 직접 정중앙을 "제한 없음"으로 바꾼다.
     * 바로 묻는 ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS는 Play 정책상 쓸 수 있는 앱이 제한돼서 쓰지 않는다.
     */
    public static Intent batteryOptimizationSettings() {
        return new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
    }

    private static Uri packageUri(Context context) {
        return Uri.parse("package:" + context.getPackageName());
    }
}
