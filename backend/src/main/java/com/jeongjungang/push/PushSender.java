package com.jeongjungang.push;

import java.util.List;

/**
 * 푸시를 실제로 보내는 곳. FCM 서비스 계정이 생기면 FCM 구현을 빈으로 등록하면 된다.
 * 그 전에는 {@link LoggingPushSender}가 보낸 척만 한다(앱은 폴링·GET /alarms로 동기화한다).
 */
public interface PushSender {

    /** 실패는 안에서 처리하고 던지지 않는다. 한 기기 실패가 다른 기기 발송을 막으면 안 된다. */
    void send(List<PushMessage> messages);
}
