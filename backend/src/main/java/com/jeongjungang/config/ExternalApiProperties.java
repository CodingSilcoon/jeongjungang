package com.jeongjungang.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external")
public record ExternalApiProperties(Odsay odsay, Kakao kakao) {

    public record Odsay(String baseUrl, String apiKey) {}

    public record Kakao(String baseUrl, String restApiKey) {}
}
