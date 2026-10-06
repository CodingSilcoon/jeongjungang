package com.jeongjungang.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class BearerTokenTest {

    private static MockHttpServletRequest withHeader(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (value != null) {
            request.addHeader("Authorization", value);
        }
        return request;
    }

    @Test
    void readsBearerToken_caseInsensitivePrefix() {
        assertThat(BearerToken.from(withHeader("Bearer abc"))).contains("abc");
        assertThat(BearerToken.from(withHeader("bearer  abc "))).contains("abc");
    }

    @Test
    void rejectsMissingWrongSchemeEmptyOrHugeTokens() {
        assertThat(BearerToken.from(withHeader(null))).isEmpty();
        assertThat(BearerToken.from(withHeader("Basic abc"))).isEmpty();
        assertThat(BearerToken.from(withHeader("Bearer "))).isEmpty();
        assertThat(BearerToken.from(withHeader("Bearer " + "a".repeat(129)))).isEmpty();
    }
}
