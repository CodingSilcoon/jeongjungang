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
import com.jeongjungang.data.repository.AccountabilityRepository;
import com.jeongjungang.ui.alarm.AlarmActivity;

/**
 * 알람 울림. 포그라운드 서비스로 소리·진동을 내고, 전체 화면 알림으로 {@link AlarmActivity}를 띄운다.
 *
 * <ul>
 *   <li>내 출발 알람: 끄면 {@link DismissQueue}에 기록하고 {@link DismissSyncWorker}가 서버에 보고한다</li>
 *   <li>에스컬레이션: 다른 멤버가 알람을 안 껐을 때 내 폰을 울린다 ({@link #startEscalation}).
 *       끄는 건 내 폰 소리만 멈추는 것이라 서버에 보고하지 않는다</li>
 * </ul>
 */
public class AlarmRingService extends Service {

    static final String ACTION_RING = "com.jeongjungang.alarm.RING";
    static final String ACTION_DISMISS = "com.jeongjungang.alarm.DISMISS";
    static final String ACTION_ESCALATE = "com.jeongjungang.alarm.ESCALATE";
    static final String EXTRA_NICKNAME = "sleeperNickname";
    static final String EXTRA_MEETING_ID = "meetingId";

    /** 이 시간 동안 아무도 안 끄면 소리를 멈춘다. 에스컬레이션은 서버가 따로 판단한다. */
    static final long MAX_RING_MS = 10 * 60 * 1000L;
    /** 에스컬레이션은 친구 폰이라 짧게 울린다. */
    static final long ESCALATION_MAX_RING_MS = 2 * 60 * 1000L;
    /** docs/API.md 8절: ESCALATION 푸시 TTL 120초. 그보다 늦게 도착하면 울리지 않는다. */
    static final long ESCALATION_TTL_MS = 120 * 1000L;
    /** 에스컬레이션 울림의 키 접두사. 내 알람 id와 섞이지 않게 한다. */
    static final String ESCALATION_PREFIX = "escalation:";
    private static final int ESCALATION_FALLBACK_NOTIFICATION_ID = 1003;

    private static final String CHANNEL_ID = "departure_alarm";
    private static final int NOTIFICATION_ID = 1001;
    private static final int MISSED_NOTIFICATION_ID = 1002;
    private static final String TAG = "AlarmRingService";
    private static final long[] VIBRATION = {0, 800, 600};

    /** 지금 울리는 키(내 알람은 알람 id, 에스컬레이션은 접두사 + 알람 id). 없으면 null. 알람 화면이 이걸 보고 스스로 닫힌다. */
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

    /** 알람 화면의 "알람 끄기" 버튼과 알림의 끄기 버튼이 쓴다. key는 {@link #ringingAlarmId()} 값. */
    public static Intent dismissIntent(Context context, String key) {
        return new Intent(context, AlarmRingService.class)
                .setAction(ACTION_DISMISS)
                .putExtra(AlarmFireReceiver.EXTRA_ALARM_ID, key);
    }

    public static boolean isEscalationKey(String key) {
        return key != null && key.startsWith(ESCALATION_PREFIX);
    }

    /**
     * 에스컬레이션 울림 시작. (3단계) FCM `ESCALATION` 데이터 메시지를 받으면 바로 부른다.
     * 받자마자 화면을 띄우지 않으면 FCM 우선순위가 깎이므로 미루지 않는다(docs/API.md 8절).
     *
     * @return 울렸으면 true. TTL이 지났거나 정확한 알람 권한이 없어 알림만 남겼으면 false
     */
    public static boolean startEscalation(Context context, String meetingId, String alarmId,
                                          String sleeperNickname, long escalatedAtMillis) {
        if (System.currentTimeMillis() - escalatedAtMillis > ESCALATION_TTL_MS) {
            Log.i(TAG, "오래된 에스컬레이션이라 울리지 않음: " + alarmId);
            return false;
        }
        if (!AlarmPermissions.canScheduleExactAlarms(context)) {
            // systemExempted 포그라운드 서비스는 정확한 알람 권한이 있어야 시작할 수 있다. 알림만 남긴다
            notifyEscalationOnly(context, sleeperNickname);
            return false;
        }
        Intent intent = new Intent(context, AlarmRingService.class)
                .setAction(ACTION_ESCALATE)
                .putExtra(AlarmFireReceiver.EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_MEETING_ID, meetingId)
                .putExtra(EXTRA_NICKNAME, sleeperNickname);
        androidx.core.content.ContextCompat.startForegroundService(context, intent);
        return true;
    }

    static String escalationTitle(String nickname) {
        return (nickname == null || nickname.isEmpty() ? "친구" : nickname) + "님이 아직 안 일어났어요";
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        String alarmId = intent == null ? null : intent.getStringExtra(AlarmFireReceiver.EXTRA_ALARM_ID);
        if (ACTION_RING.equals(action) && alarmId != null) {
            startRinging(alarmId);
        } else if (ACTION_ESCALATE.equals(action) && alarmId != null) {
            startEscalationRinging(alarmId, intent.getStringExtra(EXTRA_NICKNAME));
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
        if (ringingId != null && !ringingId.equals(alarmId) && !isEscalationKey(ringingId)) {
            // 다른 알람이 울리는 중이면 이전 것은 놓친 것으로 정리한다(에스컬레이션은 내 알람이 덮어쓴다)
            scheduler.finished(ringingId);
        }
        ringingId = alarmId;
        RINGING.setValue(alarmId);
        playSound();
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, MAX_RING_MS);
        reportRingingInBackground(alarmId);
    }

    private void startEscalationRinging(String alarmId, String nickname) {
        String key = ESCALATION_PREFIX + alarmId;
        if (ringingId != null && !isEscalationKey(ringingId)) {
            // 내 출발 알람이 울리는 중이면 끊지 않고 알림만 남긴다
            notifyEscalationOnly(this, nickname);
            return;
        }
        startInForeground(buildEscalationNotification(key, nickname));
        ringingId = key;
        RINGING.setValue(key);
        playSound();
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, ESCALATION_MAX_RING_MS);
    }

    private void dismiss(String key) {
        if (isEscalationKey(key)) {
            // 내 폰 소리만 멈춘다. 서버에 보고할 것은 없다
            if (key.equals(ringingId)) {
                stopRinging(true);
            } else if (ringingId == null) {
                stopSelf();
            }
            return;
        }
        new DismissQueue(this).add(key, System.currentTimeMillis());
        DismissSyncWorker.enqueue(this);
        if (key.equals(ringingId)) {
            stopRinging(true);
        } else {
            new AlarmScheduler(this).finished(key);
            if (ringingId == null) {
                stopSelf();
            }
        }
    }

    /** 서버에 "울리는 중" 보고(선택 기능). 실패해도 에스컬레이션은 서버가 따로 판단하므로 다시 보내지 않는다. */
    private void reportRingingInBackground(final String alarmId) {
        final AccountabilityRepository repo = AccountabilityRepository.getInstance(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                repo.reportRinging(alarmId);
            }
        }, "alarm-ringing-report").start();
    }

    /** @param dismissed 사용자가 껐으면 true, 시간이 지나 멈췄으면 false */
    private void stopRinging(boolean dismissed) {
        handler.removeCallbacks(timeout);
        stopSound();
        if (ringingId != null && !isEscalationKey(ringingId)) {
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
        ensureChannel(this);
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

    private Notification buildEscalationNotification(String key, String nickname) {
        ensureChannel(this);
        Intent screen = AlarmActivity.escalationIntent(this, key, nickname);
        PendingIntent fullScreen = PendingIntent.getActivity(this, key.hashCode(), screen,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent dismiss = PendingIntent.getService(this, key.hashCode(), dismissIntent(this, key),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(escalationTitle(nickname))
                .setContentText("출발 알람을 끄지 않았어요. 연락해 보세요.")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(fullScreen)
                .setFullScreenIntent(fullScreen, true)
                .addAction(0, "내 알람 끄기", dismiss)
                .build();
    }

    /** 울리지 못할 때(권한 없음, 내 알람이 울리는 중) 남기는 알림. */
    static void notifyEscalationOnly(Context context, String nickname) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        ensureChannel(context);
        nm.notify(ESCALATION_FALLBACK_NOTIFICATION_ID, new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(escalationTitle(nickname))
                .setContentText("출발 알람을 끄지 않았어요. 연락해 보세요.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build());
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

    private static void ensureChannel(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
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
