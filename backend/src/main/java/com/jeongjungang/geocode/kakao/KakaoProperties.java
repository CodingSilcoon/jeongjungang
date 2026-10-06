package com.jeongjungang.geocode.kakao;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 로컬 API 설정 (application.yaml의 jeongjungang.kakao.*).
 * REST 키는 환경변수 KAKAO_REST_API_KEY로만 넣는다. 비어 있으면 카카오 호출이 UPSTREAM_ERROR로 끝난다.
 */
@ConfigurationProperties(prefix = "jeongjungang.kakao")
public record KakaoProperties(String restApiKey, String baseUrl, Duration connectTimeout, Duration readTimeout) {

    public boolean hasKey() {
        return restApiKey != null && !restApiKey.isBlank();
    }
}
