package com.jeongjungang.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RateLimitRuleTest {

    @Test
    void publicEndpoints_matchIpRules() {
        assertThat(RateLimitRule.match("POST", "/api/v1/meetings", false)).contains(RateLimitRule.CREATE_MEETING);
        assertThat(RateLimitRule.match("POST", "/api/v1/meetings/by-code/7K3QH9MX/participants", false))
                .contains(RateLimitRule.JOIN_MEETING);
        assertThat(RateLimitRule.match("GET", "/api/v1/meetings/by-code/7K3QH9MX", false))
                .contains(RateLimitRule.INVITE_PREVIEW);
        assertThat(RateLimitRule.match("GET", "/api/v1/geocode/reverse", false)).contains(RateLimitRule.LOOKUP);
        assertThat(RateLimitRule.match("GET", "/api/v1/places", true)).contains(RateLimitRule.LOOKUP);
    }

    @Test
    void otherRequestsWithToken_matchTokenRule() {
        assertThat(RateLimitRule.match("GET", "/api/v1/meetings/abc", true)).contains(RateLimitRule.AUTHENTICATED);
        assertThat(RateLimitRule.match("PATCH", "/api/v1/participants/abc", true)).contains(RateLimitRule.AUTHENTICATED);
    }

    @Test
    void headRequests_followGetRules() {
        // Spring MVC는 GET 매핑에 HEAD도 처리한다. HEAD로 제한을 피하지 못하게 같은 규칙을 적용한다
        assertThat(RateLimitRule.match("HEAD", "/api/v1/geocode", false)).contains(RateLimitRule.LOOKUP);
        assertThat(RateLimitRule.match("HEAD", "/api/v1/places", false)).contains(RateLimitRule.LOOKUP);
        assertThat(RateLimitRule.match("HEAD", "/api/v1/meetings/by-code/7K3QH9MX", false))
                .contains(RateLimitRule.INVITE_PREVIEW);
        // POST 규칙은 HEAD에 적용되지 않는다
        assertThat(RateLimitRule.match("HEAD", "/api/v1/meetings", false)).isEmpty();
    }

    @Test
    void healthAndTokenlessPrivateRequests_matchNoSpecificRule() {
        assertThat(RateLimitRule.match("GET", "/api/v1/health", false)).isEmpty();
        assertThat(RateLimitRule.match("GET", "/api/v1/meetings/abc", false)).isEmpty();
    }

    @Test
    void ipCeiling_appliesToEveryApiPathButHealth_andNeverComesFromMatch() {
        assertThat(RateLimitRule.ceilingApplies("/api/v1/meetings/abc")).isTrue();
        assertThat(RateLimitRule.ceilingApplies("/api/v1/geocode")).isTrue();
        assertThat(RateLimitRule.ceilingApplies("/api/v1/health")).isFalse();
        assertThat(RateLimitRule.match("DELETE", "/api/v1/anything", false)).isEmpty();
    }
}
