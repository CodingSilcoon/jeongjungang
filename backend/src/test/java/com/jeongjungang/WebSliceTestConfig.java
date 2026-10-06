package com.jeongjungang;

import static org.mockito.Mockito.mock;

import com.jeongjungang.meeting.ParticipantRepository;
import com.jeongjungang.ratelimit.RateLimiter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * @WebMvcTest에도 토큰 인증 리졸버와 요청 제한 인터셉터가 올라온다.
 * 그 둘이 쓰는 저장소는 가짜로, 요청 제한은 항상 통과로 둔다.
 */
@TestConfiguration
public class WebSliceTestConfig {

    @Bean
    ParticipantRepository participantRepository() {
        return mock(ParticipantRepository.class);
    }

    @Bean
    RateLimiter rateLimiter() {
        return (key, limit, window) -> RateLimiter.Decision.allow();
    }
}
