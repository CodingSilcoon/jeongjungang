package com.jeongjungang.push;

import java.time.Duration;
import java.util.Map;

/**
 * 기기 하나에 보낼 FCM data 메시지 (docs/API.md 8절). 모두 priority high로 보낸다.
 * ttl이 null이면 FCM 기본값을 쓴다.
 */
public record PushMessage(String fcmToken, Map<String, String> data, Duration ttl) {

    public PushMessage {
        data = Map.copyOf(data);
    }

    public String type() {
        return data.get(PushNotifier.TYPE);
    }
}
