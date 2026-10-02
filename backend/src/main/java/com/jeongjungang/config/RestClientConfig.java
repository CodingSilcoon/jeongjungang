package com.jeongjungang.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient odsayRestClient(ExternalApiProperties props) {
        return RestClient.builder().baseUrl(props.odsay().baseUrl()).build();
    }

    @Bean
    public RestClient kakaoRestClient(ExternalApiProperties props) {
        return RestClient.builder()
                .baseUrl(props.kakao().baseUrl())
                .defaultHeader("Authorization", "KakaoAK " + props.kakao().restApiKey())
                .build();
    }
}
