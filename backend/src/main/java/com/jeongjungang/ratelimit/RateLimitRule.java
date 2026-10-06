package com.jeongjungang.ratelimit;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * 요청 제한 규칙 (docs/API.md 3절). 위에서부터 처음 맞는 규칙 하나만 적용한다.
 * 공개 API는 IP 기준, 그 외 토큰을 보낸 요청은 토큰 기준이다.
 */
enum RateLimitRule {

    LOOKUP(Subject.IP, 30, HttpMethod.GET, "/api/v1/geocode", "/api/v1/geocode/**", "/api/v1/places"),
    CREATE_MEETING(Subject.IP, 10, HttpMethod.POST, "/api/v1/meetings"),
    JOIN_MEETING(Subject.IP, 20, HttpMethod.POST, "/api/v1/meetings/by-code/*/participants"),
    INVITE_PREVIEW(Subject.IP, 30, HttpMethod.GET, "/api/v1/meetings/by-code/*"),
    AUTHENTICATED(Subject.TOKEN, 120, null, "/api/v1/**");

    static final Duration WINDOW = Duration.ofMinutes(1);

    enum Subject { IP, TOKEN }

    private final Subject subject;
    private final int limitPerWindow;
    private final HttpMethod method;
    private final List<PathPattern> patterns;

    RateLimitRule(Subject subject, int limitPerWindow, HttpMethod method, String... patterns) {
        this.subject = subject;
        this.limitPerWindow = limitPerWindow;
        this.method = method;
        this.patterns = List.of(patterns).stream().map(PathPatternParser.defaultInstance::parse).toList();
    }

    /** 토큰 규칙은 토큰을 보낸 요청에만 맞는다. 토큰 없는 요청은 인증 단계에서 401로 끝난다. */
    static Optional<RateLimitRule> match(String method, String path, boolean hasToken) {
        PathContainer container = PathContainer.parsePath(path);
        for (RateLimitRule rule : values()) {
            if (rule.subject == Subject.TOKEN && !hasToken) {
                continue;
            }
            if (rule.method != null && !rule.method.matches(method)) {
                continue;
            }
            if (rule.patterns.stream().anyMatch(p -> p.matches(container))) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }

    Subject subject() {
        return subject;
    }

    int limitPerWindow() {
        return limitPerWindow;
    }
}
