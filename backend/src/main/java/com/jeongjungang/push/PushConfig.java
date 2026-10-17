package com.jeongjungang.push;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PushConfig {

    /** 다른 PushSender(FCM, 테스트용 기록기)가 있으면 그쪽을 쓴다. */
    @Bean
    @ConditionalOnMissingBean(PushSender.class)
    PushSender loggingPushSender() {
        return new LoggingPushSender();
    }
}
