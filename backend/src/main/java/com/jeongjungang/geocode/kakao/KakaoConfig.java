package com.jeongjungang.geocode.kakao;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class KakaoConfig {

    /** 카카오가 느려도 앱 요청이 오래 묶이지 않게 타임아웃을 짧게 둔다. */
    @Bean
    KakaoLocalClient kakaoLocalClient(RestClient.Builder builder, KakaoProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        RestClient http = builder.baseUrl(properties.baseUrl()).requestFactory(factory).build();
        return new KakaoLocalClient(http, properties);
    }
}
