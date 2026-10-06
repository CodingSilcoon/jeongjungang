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
    void healthAndTokenlessPrivateRequests_areNotLimited() {
        assertThat(RateLimitRule.match("GET", "/api/v1/health", false)).isEmpty();
        assertThat(RateLimitRule.match("GET", "/api/v1/meetings/abc", false)).isEmpty();
    }
}
