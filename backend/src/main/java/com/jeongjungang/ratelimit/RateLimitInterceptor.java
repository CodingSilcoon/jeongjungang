package com.jeongjungang.ratelimit;

import com.jeongjungang.auth.BearerToken;
import com.jeongjungang.auth.ParticipantTokens;
import com.jeongjungang.ratelimit.RateLimiter.Decision;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 컨트롤러에 닿기 전에 요청 횟수를 센다.
 * Redis가 죽어도 서비스는 계속 돌아가야 하므로, 셀 수 없을 때는 경고만 남기고 통과시킨다.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimiter limiter;

    public RateLimitInterceptor(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (RateLimitRule.ceilingApplies(path)) {
            check(RateLimitRule.IP_CEILING, request.getRemoteAddr());
        }
        Optional<String> token = BearerToken.from(request);
        Optional<RateLimitRule> rule = RateLimitRule.match(request.getMethod(), path, token.isPresent());
        if (rule.isEmpty()) {
            return true;
        }
        String subject = rule.get().subject() == RateLimitRule.Subject.TOKEN
                ? ParticipantTokens.hash(token.orElseThrow())
                : request.getRemoteAddr();
        check(rule.get(), subject);
        return true;
    }

    private void check(RateLimitRule rule, String subject) {
        Decision decision;
        try {
            decision = limiter.tryAcquire(rule.name() + ":" + subject, rule.limitPerWindow(), RateLimitRule.WINDOW);
        } catch (DataAccessException | IllegalStateException e) {
            log.warn("요청 제한을 확인하지 못해 통과시킵니다: {}", e.toString());
            return;
        }
        if (!decision.allowed()) {
            throw new RateLimitedException(decision.retryAfterSeconds());
        }
    }
}
