package com.jeongjungang.health;

import com.jeongjungang.common.ApiResponse;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 앱이 서버 상태를 확인하는 엔드포인트 (docs/API.md 5절). 운영 점검용 /actuator/health와 별개다. */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private static final String UNKNOWN_VERSION = "dev";

    private final Clock clock;
    private final String version;

    public HealthController(Clock clock, ObjectProvider<BuildProperties> buildProperties) {
        this.clock = clock;
        BuildProperties build = buildProperties.getIfAvailable();
        this.version = build != null && build.getVersion() != null ? build.getVersion() : UNKNOWN_VERSION;
    }

    @GetMapping("/health")
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.ok(new HealthResponse("UP", version, Instant.now(clock)));
    }

    public record HealthResponse(String status, String version, Instant serverTime) {}
}
