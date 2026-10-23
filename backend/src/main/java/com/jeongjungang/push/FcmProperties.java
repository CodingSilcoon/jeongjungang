package com.jeongjungang.push;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FCM 설정 (application.yaml의 jeongjungang.fcm.*).
 * 서비스 계정 키(JSON)는 base64로 바꿔 환경변수 FCM_CREDENTIALS_BASE64로만 넣는다. 비어 있으면 푸시를 보내지 않고
 * {@link LoggingPushSender}가 로그만 남긴다(로컬·테스트).
 */
@ConfigurationProperties(prefix = "jeongjungang.fcm")
public record FcmProperties(String credentialsBase64, String baseUrl, Duration connectTimeout, Duration readTimeout) {

    public boolean hasCredentials() {
        return credentialsBase64 != null && !credentialsBase64.isBlank();
    }

    /** 값이 로그나 예외 메시지에 찍히지 않게 한다. */
    @Override
    public String toString() {
        return "FcmProperties[credentials=" + (hasCredentials() ? "set" : "empty") + ", baseUrl=" + baseUrl + "]";
    }
}
