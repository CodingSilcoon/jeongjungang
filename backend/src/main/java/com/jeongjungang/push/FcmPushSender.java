package com.jeongjungang.push;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * FCM HTTP v1 API로 data 메시지를 보낸다 (https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages/send).
 * firebase-admin은 Firestore 등 안 쓰는 의존성이 커서(서버 메모리 512MB) 인증 라이브러리만 쓰고 API를 직접 부른다.
 * 기기마다 따로 보내고, 한 기기 실패는 로그만 남긴다. FCM 토큰은 기기 식별 정보라 로그에 남기지 않는다.
 */
class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);

    /** 서비스 계정 액세스 토큰. 만료되면 새로 받아서 돌려준다. */
    @FunctionalInterface
    interface AccessTokens {
        String get() throws IOException;
    }

    private final RestClient http;
    private final String projectId;
    private final AccessTokens accessTokens;
    private final Consumer<String> onUnregistered;

    /**
     * @param onUnregistered 앱이 지워졌거나 토큰이 만료돼 FCM이 404(UNREGISTERED)를 준 토큰. 기기 행을 지우는 데 쓴다
     */
    FcmPushSender(RestClient http, String projectId, AccessTokens accessTokens, Consumer<String> onUnregistered) {
        this.http = http;
        this.projectId = projectId;
        this.accessTokens = accessTokens;
        this.onUnregistered = onUnregistered;
    }

    @Override
    public void send(List<PushMessage> messages) {
        if (messages.isEmpty()) {
            return;
        }
        String accessToken;
        try {
            accessToken = accessTokens.get();
        } catch (IOException | RuntimeException e) {
            log.warn("FCM 액세스 토큰을 받지 못해 푸시 {}건을 보내지 못했습니다", messages.size(), e);
            return;
        }
        int failed = 0;
        for (PushMessage message : messages) {
            if (!sendOne(message, accessToken)) {
                failed++;
            }
        }
        log.info("FCM 푸시 {}건 보냄, 실패 {}건: {}", messages.size() - failed, failed,
                messages.stream().map(PushMessage::type).distinct().toList());
    }

    private boolean sendOne(PushMessage message, String accessToken) {
        try {
            http.post()
                    .uri("/v1/projects/{projectId}/messages:send", projectId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", body(message)))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                log.info("FCM 토큰이 더 이상 유효하지 않아 기기를 지웁니다 ({})", message.type());
                removeQuietly(message.fcmToken());
            } else {
                log.warn("FCM 푸시 실패 ({}): HTTP {}", message.type(), e.getStatusCode().value());
            }
            return false;
        } catch (RestClientException e) {
            log.warn("FCM 푸시 실패 ({}): {}", message.type(), e.getClass().getSimpleName());
            return false;
        }
    }

    private void removeQuietly(String fcmToken) {
        try {
            onUnregistered.accept(fcmToken);
        } catch (RuntimeException e) {
            log.warn("만료된 FCM 토큰의 기기를 지우지 못했습니다", e);
        }
    }

    /** docs/API.md 8절: 모두 data 메시지, priority high. ttl이 있으면 그 뒤엔 버린다. */
    static Map<String, Object> body(PushMessage message) {
        Map<String, Object> android = new LinkedHashMap<>();
        android.put("priority", "HIGH");
        if (message.ttl() != null) {
            android.put("ttl", message.ttl().toSeconds() + "s");
        }
        return Map.of("token", message.fcmToken(), "data", message.data(), "android", android);
    }
}
