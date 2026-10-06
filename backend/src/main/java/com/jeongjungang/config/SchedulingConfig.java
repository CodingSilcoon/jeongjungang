package com.jeongjungang.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 만료 약속 삭제(그리고 3단계 에스컬레이션 확인) 같은 주기 작업을 켠다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
