package com.jeongjungang.push;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.jeongjungang.alarm.AlarmRingService;
import com.jeongjungang.data.repository.AccountabilityRepository;

/**
 * FCM 수신 (docs/API.md 8절).
 * <ul>
 *   <li>ESCALATION: 받자마자 풀스크린 알람을 띄운다. 미루면 FCM이 이 앱의 우선순위를 깎는다</li>
 *   <li>ALARM_SYNC: 서버의 내 알람을 기기 알람과 맞춘다({@link AlarmSyncWorker})</li>
 *   <li>ALARM_CANCELLED: 기기 알람을 바로 지우고, 순서가 뒤바뀐 푸시에 대비해 서버 상태로 한 번 더 맞춘다</li>
 * </ul>
 * 네트워크가 필요한 일은 여기서 하지 않고 WorkManager로 넘긴다(수신 콜백은 시간 제한이 짧다).
 */
public class PushMessagingService extends FirebaseMessagingService {

    private static final String TAG = "PushMessaging";

    @Override
    public void onNewToken(@NonNull String token) {
        // 토큰이 바뀌면 들어가 있는 약속마다 다시 등록해야 서버가 새 토큰으로 보낸다
        DeviceRegisterWorker.enqueue(this);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        PushPayload p = PushPayload.parse(message.getData());
        if (p == null) {
            Log.w(TAG, "모르는 푸시라 무시: " + message.getData().get("type"));
            return;
        }
        switch (p.type) {
            case ESCALATION:
                AlarmRingService.startEscalation(this, p.meetingId, p.alarmId, p.sleeperNickname,
                        p.escalatedAtMillis);
                break;
            case ALARM_CANCELLED:
                AccountabilityRepository.getInstance(this).cancelLocal(p.meetingId);
                AlarmSyncWorker.enqueue(this, p.meetingId);
                break;
            case ALARM_SYNC:
                AlarmSyncWorker.enqueue(this, p.meetingId);
                break;
        }
    }
}
