package com.jeongjungang.push;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 통합 테스트용: 보낸 푸시를 기록만 한다. */
public class RecordingPushSender implements PushSender {

    private final List<PushMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(List<PushMessage> messages) {
        sent.addAll(messages);
    }

    public List<PushMessage> sent() {
        return List.copyOf(sent);
    }

    /** 이 종류로 보낸 메시지의 FCM 토큰들. */
    public List<String> tokensOf(String type) {
        return sent.stream().filter(m -> type.equals(m.type())).map(PushMessage::fcmToken).toList();
    }

    public void clear() {
        sent.clear();
    }
}
