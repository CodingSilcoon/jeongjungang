package com.jeongjungang;

import com.jeongjungang.push.RecordingPushSender;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 실제 Postgres·Redis 컨테이너로 전체 컨텍스트를 띄우는 통합 테스트의 부모.
 * 컨테이너는 테스트 실행 전체에서 한 번만 띄운다(@Container를 쓰면 클래스마다 꺼져서, 재사용되는 Spring 컨텍스트가 죽은 컨테이너를 본다).
 * Spring Boot가 처음 연결할 때 시작하고 JVM이 끝날 때 정리한다. Docker가 없으면 건너뛴다.
 * 테스트마다 Redis를 비워서 요청 제한 횟수가 다른 테스트로 넘어가지 않게 한다.
 * 에스컬레이션 주기 작업은 사실상 끄고, 테스트가 직접 부른다(1초마다 돌면 테스트와 경쟁한다).
 * 푸시는 보내지 않고 {@link RecordingPushSender}에 기록한다.
 */
@SpringBootTest(properties = {
        "jeongjungang.escalation.poll-interval=PT1H",
        "jeongjungang.escalation.reseed-interval=PT1H",
        "jeongjungang.escalation.reseed-initial-delay=PT1H"})
@Import(IntegrationTestSupport.TestPushConfig.class)
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTestSupport {

    private static final int REDIS_PORT = 6379;

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(REDIS_PORT);

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    protected RecordingPushSender pushes;

    @BeforeEach
    void clearRedisAndPushes() {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        pushes.clear();
    }

    @TestConfiguration
    static class TestPushConfig {

        @Bean
        @Primary
        RecordingPushSender recordingPushSender() {
            return new RecordingPushSender();
        }
    }
}
