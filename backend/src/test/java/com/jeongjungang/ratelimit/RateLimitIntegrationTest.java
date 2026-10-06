package com.jeongjungang.ratelimit;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jeongjungang.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 실제 Redis로 약속 만들기 제한(IP당 10회/분)을 확인한다. */
@AutoConfigureMockMvc
class RateLimitIntegrationTest extends IntegrationTestSupport {

    private static final String BODY = "{\"hostNickname\":\"민수\"}";

    @Autowired
    private MockMvc mvc;

    private static MockHttpServletRequestBuilder createFrom(String ip) {
        return post("/api/v1/meetings").contentType(MediaType.APPLICATION_JSON).content(BODY)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                });
    }

    @Test
    void eleventhCreateInAMinute_isRejectedWithRetryAfter_otherIpsUnaffected() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(createFrom("203.0.113.7")).andExpect(status().isCreated());
        }

        mvc.perform(createFrom("203.0.113.7"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
                .andExpect(header().string("Retry-After", matchesPattern("[1-9]|[1-5][0-9]|60")));
        mvc.perform(createFrom("203.0.113.8")).andExpect(status().isCreated());
    }
}
