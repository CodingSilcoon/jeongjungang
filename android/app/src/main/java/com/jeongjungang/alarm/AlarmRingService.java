package com.jeongjungang.alarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.jeongjungang.ui.alarm.AlarmActivity;

/**
 * 알람 울림. 포그라운드 서비스로 소리·진동을 내고, 전체 화면 알림으로 {@link AlarmActivity}를 띄운다.
 * 끄면 {@link DismissQueue}에 기록한다(3단계에서 서버에 보고).
 */
public class AlarmRingService extends Service {

    static final String ACTION_RING = "com.jeongjungang.alarm.RING";
    static final String ACTION_DISMISS = "com.jeongjungang.alarm.DISMISS";

    /** 이 시간 동안 아무도 안 끄면 소리를 멈춘다. 에스컬레이션은 서버가 따로 판단한다. */
    static final long MAX_RING_MS = 10 * 60 * 1000L;

    private static final String CHANNEL_ID = "departure_alarm";
    private static final int NOTIFICATION_ID = 1001;
    private static final int MISSED_NOTIFICATION_ID = 1002;
    private static final String TAG = "AlarmRingService";
    private static final long[] VIBRATION = {0, 800, 600};

    /** 지금 울리는 알람 id. 없으면 null. 알람 화면이 이걸 보고 스스로 닫힌다. */
    private static final MutableLiveData<String> RINGING = new MutableLiveData<String>(null);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = new Runnable() {
        @Override
        public void run() {
            stopRinging(false);
        }
    };
    private Ringtone ringtone;
    private Vibrator vibrator;
    private String ringingId;

    public static LiveData<String> ringingAlarmId() {
        return RINGING;
    }

    static Intent ringIntent(Context context, String alarmId) {
        return new Intent(context, AlarmRingService.class)
                .setAction(ACTION_RING)
                .putExtra(AlarmFireReceiver.EXTRA_ALARM_ID, alarmId);
    }

    /** 알람 화면의 "알람 끄기" 버튼과 알림의 끄기 버튼이 쓴다. */
    public static Intent dismissIntent(Context context, String alarmId) {
        return new Intent(context, AlarmRingService.class)
                .setAction(ACTION_DISMISS)
                .putExtra(AlarmFireReceiver.EXTRA_ALARM_ID, alarmId);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        String alarmId = intent == null ? null : intent.getStringExtra(AlarmFireReceiver.EXTRA_ALARM_ID);
        if (ACTION_RING.equals(action) && alarmId != null) {
            startRinging(alarmId);
        } else if (ACTION_DISMISS.equals(action) && alarmId != null) {
            dismiss(alarmId);
        } else if (ringingId == null) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void startRinging(String alarmId) {
        AlarmScheduler scheduler = new AlarmScheduler(this);
        AlarmSpec spec = scheduler.find(alarmId);
        if (spec == null) {
            spec = new AlarmSpec(alarmId, System.currentTimeMillis(), "출발할 시간이에요", null, 0);
        }
        // 포그라운드 시작은 5초 안에 해야 해서 가장 먼저 한다
        startInForeground(buildRingingNotification(spec));
        if (ringingId != null && !ringingId.equals(alarmId)) {
            // 다른 알람이 울리는 중이면 이전 것은 놓친 것으로 정리한다
            scheduler.finished(ringingId);
        }
        ringingId = alarmId;
        RINGING.setValue(alarmId);
        playSound();
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, MAX_RING_MS);
    }

    private void dismiss(String alarmId) {
        new DismissQueue(this).add(alarmId, System.currentTimeMillis());
        if (alarmId.equals(ringingId)) {
            stopRinging(true);
        } else {
            new AlarmScheduler(this).finished(alarmId);
            if (ringingId == null) {
                stopSelf();
            }
        }
    }

    /** @param dismissed 사용자가 껐으면 true, 시간이 지나 멈췄으면 false */
    private void stopRinging(boolean dismissed) {
        handler.removeCallbacks(timeout);
        stopSound();
        if (ringingId != null) {
            AlarmSpec spec = new AlarmScheduler(this).find(ringingId);
            new AlarmScheduler(this).finished(ringingId);
            if (!dismissed) {
                notifyMissed(spec);
            }
        }
        ringingId = null;
        RINGING.setValue(null);
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void startInForeground(Notification notification) {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED : 0;
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type);
    }

    private Notification buildRingingNotification(AlarmSpec spec) {
        ensureChannel();
        Intent screen = AlarmActivity.intent(this, spec.id);
        PendingIntent fullScreen = PendingIntent.getActivity(this, spec.id.hashCode(), screen,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent dismiss = PendingIntent.getService(this, spec.id.hashCode(), dismissIntent(this, spec.id),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(spec.title)
                .setContentText(AlarmTexts.subtitle(spec))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(fullScreen)
                .setFullScreenIntent(fullScreen, true)
                .addAction(0, "알람 끄기", dismiss)
                .build();
    }

    private void notifyMissed(AlarmSpec spec) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null || spec == null) {
            return;
        }
        nm.notify(MISSED_NOTIFICATION_ID, new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("출발 알람을 놓쳤어요")
                .setContentText(spec.title)
                .setAutoCancel(true)
                .build());
    }

    private void ensureChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "출발 알람", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("약속 장소로 출발할 시간을 알려 줍니다.");
        // 소리·진동은 서비스가 직접 낸다(알림 소리는 한 번만 나서 알람에 맞지 않음)
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(channel);
    }

    private void playSound() {
        stopSound();
        AudioAttributes alarmAudio = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        Uri uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
        if (uri == null) {
            uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        }
        try {
            ringtone = RingtoneManager.getRingtone(this, uri);
            if (ringtone != null) {
                ringtone.setAudioAttributes(alarmAudio);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ringtone.setLooping(true);
                }
                ringtone.play();
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "알람 소리 재생 실패. 진동만 울림", e);
        }
        vibrator = getSystemService(Vibrator.class);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(VIBRATION, 0), alarmAudio);
        }
    }

    private void stopSound() {
        if (ringtone != null) {
            ringtone.stop();
            ringtone = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(timeout);
        stopSound();
        if (ringingId != null) {
            RINGING.setValue(null);
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
