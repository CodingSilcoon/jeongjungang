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
 * 이와 별개로 헬스 체크를 뺀 모든 요청은 IP 상한({@link #IP_CEILING})도 함께 센다.
 */
enum RateLimitRule {

    LOOKUP(Subject.IP, 30, HttpMethod.GET, "/api/v1/geocode", "/api/v1/geocode/**", "/api/v1/places"),
    CREATE_MEETING(Subject.IP, 10, HttpMethod.POST, "/api/v1/meetings"),
    JOIN_MEETING(Subject.IP, 20, HttpMethod.POST, "/api/v1/meetings/by-code/*/participants"),
    INVITE_PREVIEW(Subject.IP, 30, HttpMethod.GET, "/api/v1/meetings/by-code/*"),
    AUTHENTICATED(Subject.TOKEN, 120, null, "/api/v1/**"),
    /**
     * 위 규칙과 별개로 거는 IP 상한. {@link #match}에는 나오지 않는다.
     * 어느 규칙에도 안 맞는 요청이나, 요청마다 가짜 토큰을 바꿔 토큰 카운터를 피하는 요청도 여기서 막힌다.
     */
    IP_CEILING(Subject.IP, 600, null, "/api/v1/**");

    private static final PathPattern HEALTH = PathPatternParser.defaultInstance.parse("/api/v1/health");

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
            if (rule == IP_CEILING) {
                continue;
            }
            if (rule.subject == Subject.TOKEN && !hasToken) {
                continue;
            }
            if (rule.method != null && !methodMatches(rule.method, method)) {
                continue;
            }
            if (rule.patterns.stream().anyMatch(p -> p.matches(container))) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }

    /** 헬스 체크를 뺀 모든 API 요청은 IP 상한을 센다. */
    static boolean ceilingApplies(String path) {
        PathContainer container = PathContainer.parsePath(path);
        return IP_CEILING.patterns.get(0).matches(container) && !HEALTH.matches(container);
    }

    /**
     * Spring MVC는 GET 매핑에 HEAD 요청도 그대로 처리한다(본문만 빼고). 그래서 GET 규칙은 HEAD에도 적용하고,
     * 같은 규칙 이름이라 같은 카운터를 쓴다. 이게 없으면 HEAD로 제한 없이 카카오를 부르거나 초대 코드를 대입할 수 있다.
     */
    private static boolean methodMatches(HttpMethod ruleMethod, String requestMethod) {
        return ruleMethod.matches(requestMethod)
                || (ruleMethod == HttpMethod.GET && HttpMethod.HEAD.matches(requestMethod));
    }

    Subject subject() {
        return subject;
    }

    int limitPerWindow() {
        return limitPerWindow;
    }
}
