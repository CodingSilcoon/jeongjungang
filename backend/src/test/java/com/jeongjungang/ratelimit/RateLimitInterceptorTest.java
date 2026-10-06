package com.jeongjungang.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jeongjungang.ratelimit.RateLimiter.Decision;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitInterceptorTest {

    private static MockHttpServletRequest request(String method, String path, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        return request;
    }

    @Test
    void ipRules_keyByRemoteAddress_tokenRule_keyByTokenHash() {
        List<String> keys = new ArrayList<>();
        RateLimitInterceptor interceptor = new RateLimitInterceptor((key, limit, window) -> {
            keys.add(key + "/" + limit);
            return Decision.allow();
        });
        MockHttpServletRequest authed = request("GET", "/api/v1/meetings/abc", "10.0.0.1");
        authed.addHeader("Authorization", "Bearer secret-token");

        interceptor.preHandle(request("POST", "/api/v1/meetings", "10.0.0.1"), new MockHttpServletResponse(), null);
        interceptor.preHandle(authed, new MockHttpServletResponse(), null);

        assertThat(keys.get(0)).isEqualTo("CREATE_MEETING:10.0.0.1/10");
        assertThat(keys.get(1)).startsWith("AUTHENTICATED:").endsWith("/120").doesNotContain("secret-token");
    }

    @Test
    void rejected_throwsWithRetryAfter() {
        RateLimitInterceptor interceptor = new RateLimitInterceptor((key, limit, window) -> Decision.reject(42));

        assertThatThrownBy(() -> interceptor.preHandle(request("POST", "/api/v1/meetings", "10.0.0.1"),
                new MockHttpServletResponse(), null))
                .isInstanceOfSatisfying(RateLimitedException.class,
                        e -> assertThat(e.retryAfterSeconds()).isEqualTo(42));
    }

    @Test
    void redisDown_letsRequestThrough() {
        RateLimitInterceptor interceptor = new RateLimitInterceptor((key, limit, window) -> {
            throw new RedisConnectionFailureException("down");
        });

        assertThat(interceptor.preHandle(request("POST", "/api/v1/meetings", "10.0.0.1"),
                new MockHttpServletResponse(), null)).isTrue();
    }
}
