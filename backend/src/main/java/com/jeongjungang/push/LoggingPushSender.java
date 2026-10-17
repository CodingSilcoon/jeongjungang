package com.jeongjungang.push;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** FCM 연동 전 기본 구현. 종류와 개수만 로그로 남긴다. FCM 토큰은 기기 식별 정보라 남기지 않는다. */
class LoggingPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public void send(List<PushMessage> messages) {
        if (messages.isEmpty()) {
            return;
        }
        log.info("푸시 {}건 (FCM 미연동, 보내지 않음): {}", messages.size(),
                messages.stream().map(PushMessage::type).distinct().toList());
    }
}
