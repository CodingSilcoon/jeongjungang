package com.jeongjungang.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 서버 설정 (application.yaml의 jeongjungang.*).
 * publicBaseUrl은 초대 링크 앞부분이다. 예: https://jeongjungang.duckdns.org
 */
@Validated
@ConfigurationProperties(prefix = "jeongjungang")
public record AppProperties(@NotBlank String publicBaseUrl) {

    public AppProperties {
        if (publicBaseUrl != null && publicBaseUrl.endsWith("/")) {
            publicBaseUrl = publicBaseUrl.substring(0, publicBaseUrl.length() - 1);
        }
    }
}
