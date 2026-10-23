package com.jeongjungang.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FcmPushSenderTest {

    private static final String BASE = "https://fcm.googleapis.test";
    private static final String SEND_URL = BASE + "/v1/projects/test-project/messages:send";

    private MockRestServiceServer server;
    private RestClient http;
    private final List<String> removed = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        http = builder.build();
    }

    private FcmPushSender sender(FcmPushSender.AccessTokens tokens) {
        return new FcmPushSender(http, "test-project", tokens, removed::add);
    }

    private static PushMessage escalation(String token) {
        return new PushMessage(token, Map.of("type", "ESCALATION", "meetingId", "m1"), Duration.ofSeconds(120));
    }

    @Test
    void sendsHighPriorityDataMessage_withBearerToken_andTtl() {
        server.expect(once(), requestTo(SEND_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer access-1"))
                .andExpect(content().json("""
                        {"message":{"token":"device-a",
                          "data":{"type":"ESCALATION","meetingId":"m1"},
                          "android":{"priority":"HIGH","ttl":"120s"}}}""", JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"name\":\"projects/test-project/messages/1\"}", MediaType.APPLICATION_JSON));

        sender(() -> "access-1").send(List.of(escalation("device-a")));

        server.verify();
        assertThat(removed).isEmpty();
    }

    @Test
    void withoutTtl_leavesFcmDefault() {
        PushMessage sync = new PushMessage("device-a", Map.of("type", "ALARM_SYNC", "meetingId", "m1"), null);

        assertThat(FcmPushSender.body(sync).get("android")).isEqualTo(Map.of("priority", "HIGH"));
    }

    @Test
    void unregisteredToken_isRemoved_andOtherDevicesStillGetIt() {
        server.expect(once(), requestTo(SEND_URL))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":404,\"status\":\"NOT_FOUND\"}}"));
        server.expect(once(), requestTo(SEND_URL))
                .andExpect(content().json("{\"message\":{\"token\":\"device-b\"}}"))
                .andRespond(withSuccess());

        sender(() -> "access-1").send(List.of(escalation("device-a"), escalation("device-b")));

        server.verify();
        assertThat(removed).containsExactly("device-a");
    }

    @Test
    void serverError_isSwallowed_andTokenKept() {
        server.expect(once(), requestTo(SEND_URL)).andRespond(withServerError());

        sender(() -> "access-1").send(List.of(escalation("device-a")));

        server.verify();
        assertThat(removed).isEmpty();
    }

    @Test
    void accessTokenFailure_sendsNothing_andDoesNotThrow() {
        sender(() -> {
            throw new IOException("oauth down");
        }).send(List.of(escalation("device-a")));

        server.verify();
    }

    @Test
    void emptyBatch_doesNotFetchAccessToken() {
        sender(() -> {
            throw new AssertionError("토큰을 받으면 안 된다");
        }).send(List.of());
    }
}
