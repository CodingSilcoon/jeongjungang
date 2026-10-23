package com.jeongjungang.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class PushConfig {

    private static final String FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    /**
     * FCM 키가 있으면 FCM으로 보내고, 없으면 로그만 남긴다.
     * 다른 PushSender(테스트용 기록기)가 있으면 그쪽을 쓴다.
     */
    @Bean
    @ConditionalOnMissingBean(PushSender.class)
    PushSender pushSender(FcmProperties properties, RestClient.Builder builder, DeviceService devices) {
        if (!properties.hasCredentials()) {
            return new LoggingPushSender();
        }
        ServiceAccountCredentials account = serviceAccount(properties.credentialsBase64());
        GoogleCredentials credentials = account.createScoped(List.of(FCM_SCOPE));
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        RestClient http = builder.baseUrl(properties.baseUrl()).requestFactory(factory).build();
        return new FcmPushSender(http, account.getProjectId(), () -> {
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        }, devices::removeByFcmToken);
    }

    /** 키가 잘못됐으면 서버를 띄우지 않는다. 푸시가 조용히 안 나가는 것보다 배포 때 바로 아는 편이 낫다. */
    private static ServiceAccountCredentials serviceAccount(String base64) {
        byte[] json;
        try {
            json = Base64.getMimeDecoder().decode(base64.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("FCM_CREDENTIALS_BASE64가 base64 형식이 아닙니다");
        }
        try {
            ServiceAccountCredentials account = ServiceAccountCredentials.fromStream(new ByteArrayInputStream(json));
            if (account.getProjectId() == null) {
                throw new IllegalStateException("FCM 서비스 계정 키에 project_id가 없습니다");
            }
            return account;
        } catch (IOException e) {
            throw new UncheckedIOException("FCM 서비스 계정 키를 읽지 못했습니다", e);
        }
    }
}
