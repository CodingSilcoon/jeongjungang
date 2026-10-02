package com.jeongjungang.data.remote.meeting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.jeongjungang.data.remote.ApiException;
import com.jeongjungang.data.remote.ApiHttp;
import com.jeongjungang.data.remote.meeting.AccountabilityApi.AlarmStatus;
import com.jeongjungang.data.remote.meeting.AccountabilityApi.DismissSource;
import com.jeongjungang.data.remote.meeting.AccountabilityApi.MyAlarm;
import com.jeongjungang.testing.FakeServer;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** docs/API.md 7절 예시 그대로 요청·응답 변환을 확인한다. */
public class HttpAccountabilityApiTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private FakeServer server;
    private HttpAccountabilityApi api;

    @Before
    public void start() throws IOException {
        server = new FakeServer();
        api = new HttpAccountabilityApi(new ApiHttp(server.baseUrl()), SEOUL);
    }

    @After
    public void stop() throws IOException {
        server.close();
    }

    private static String ok(String data) {
        return "{\"success\":true,\"data\":" + data + ",\"error\":null}";
    }

    private static long at(String iso) {
        return OffsetDateTime.parse(iso).toInstant().toEpochMilli();
    }

    @Test
    public void reportTravelIsPutWithRoundedMinutes() throws Exception {
        server.enqueue(204, "");
        api.reportTravel("p-1", "tok", "답십리역", 26.94, 1);
        FakeServer.Request req = server.last();
        assertEquals("PUT", req.method);
        assertEquals("/api/v1/participants/p-1/travel", req.target);
        assertEquals("Bearer tok", req.header("Authorization"));
        JSONObject body = new JSONObject(req.body);
        assertEquals("답십리역", body.getString("placeName"));
        assertEquals(26.9, body.getDouble("minutes"), 1e-9);
        assertEquals(1, body.getInt("transfers"));
    }

    @Test
    public void enableSendsGraceAndMarginAndValidatesRange() throws Exception {
        server.enqueue(200, ok("{\"enabled\":true,\"gracePeriodSec\":60,\"marginMinutes\":5}"));
        api.enable("m-1", "tok", 60, 5);
        assertEquals("POST", server.last().method);
        assertEquals("/api/v1/meetings/m-1/accountability", server.last().target);
        JSONObject body = new JSONObject(server.last().body);
        assertEquals(60, body.getInt("gracePeriodSec"));
        assertEquals(5, body.getInt("marginMinutes"));

        int before = server.requests().size();
        expectValidation(() -> api.enable("m-1", "tok", 29, 5));
        expectValidation(() -> api.enable("m-1", "tok", 301, 5));
        expectValidation(() -> api.enable("m-1", "tok", 60, -1));
        assertEquals(before, server.requests().size());
    }

    @Test
    public void enableConflictKeepsServerCode() {
        server.enqueue(409, "{\"success\":false,\"data\":null,\"error\":{\"code\":\"NOT_ALL_OPTED_IN\",\"message\":\"x\"}}");
        try {
            api.enable("m-1", "tok", 60, 5);
            fail();
        } catch (ApiException e) {
            assertEquals("NOT_ALL_OPTED_IN", e.code);
            assertEquals(409, e.httpStatus);
        }
    }

    @Test
    public void disableAndRinging() throws Exception {
        server.enqueue(204, "");
        api.disable("m-1", "tok");
        assertEquals("DELETE", server.last().method);
        assertEquals("/api/v1/meetings/m-1/accountability", server.last().target);

        server.enqueue(204, "");
        api.ringing("a-1", "tok");
        assertEquals("POST", server.last().method);
        assertEquals("/api/v1/alarms/a-1/ringing", server.last().target);
    }

    @Test
    public void registerDevice() throws Exception {
        server.enqueue(204, "");
        api.registerDevice("tok", "fcm-123", "0.1.0");
        assertEquals("/api/v1/devices", server.last().target);
        JSONObject body = new JSONObject(server.last().body);
        assertEquals("fcm-123", body.getString("fcmToken"));
        assertEquals("ANDROID", body.getString("platform"));
        assertEquals("0.1.0", body.getString("appVersion"));
    }

    @Test
    public void myAlarmParsesSpecExample() throws Exception {
        server.enqueue(200, ok("{\"accountability\":{\"enabled\":true,\"gracePeriodSec\":60},"
                + "\"alarm\":{\"id\":\"9b7e\",\"fireAt\":\"2026-10-10T17:53:00+09:00\","
                + "\"graceUntil\":\"2026-10-10T17:54:00+09:00\",\"status\":\"SCHEDULED\"},"
                + "\"serverTime\":\"2026-10-10T10:00:00Z\"}"));
        MyAlarm a = api.myAlarm("m-1", "tok");
        assertEquals("GET", server.last().method);
        assertEquals("/api/v1/meetings/m-1/alarms", server.last().target);
        assertTrue(a.enabled);
        assertEquals("9b7e", a.alarmId);
        assertEquals(at("2026-10-10T17:53:00+09:00"), a.fireAtMillis);
        assertEquals(at("2026-10-10T17:54:00+09:00"), a.graceUntilMillis);
        assertEquals(AlarmStatus.SCHEDULED, a.status);
        assertTrue(a.status.shouldRing());
        assertEquals(at("2026-10-10T10:00:00Z"), a.serverTimeMillis);
    }

    @Test
    public void myAlarmWithoutAlarmYet() throws Exception {
        server.enqueue(200, ok("{\"accountability\":{\"enabled\":false,\"gracePeriodSec\":60},\"alarm\":null}"));
        MyAlarm a = api.myAlarm("m-1", "tok");
        assertFalse(a.enabled);
        assertNull(a.alarmId);
        assertEquals(0, a.serverTimeMillis);
    }

    @Test
    public void dismissSendsIsoTimeAndSourceAndReadsStatus() throws Exception {
        server.enqueue(200, ok("{\"status\":\"DISMISSED\"}"));
        AlarmStatus s = api.dismiss("9b7e", "tok", at("2026-10-10T17:53:20+09:00"), DismissSource.DEVICE);
        assertEquals("POST", server.last().method);
        assertEquals("/api/v1/alarms/9b7e/dismiss", server.last().target);
        JSONObject body = new JSONObject(server.last().body);
        assertEquals("2026-10-10T17:53:20+09:00", body.getString("dismissedAt"));
        assertEquals("DEVICE", body.getString("source"));
        assertEquals(AlarmStatus.DISMISSED, s);

        server.enqueue(200, ok("{\"status\":\"ESCALATED\"}"));
        assertEquals(AlarmStatus.ESCALATED, api.dismiss("9b7e", "tok", 0, DismissSource.MANUAL_AWAKE));
        assertEquals("MANUAL_AWAKE", new JSONObject(server.last().body).getString("source"));
    }

    @Test
    public void onlyScheduledAndRingingShouldRing() {
        assertTrue(AlarmStatus.SCHEDULED.shouldRing());
        assertTrue(AlarmStatus.RINGING.shouldRing());
        assertFalse(AlarmStatus.DISMISSED.shouldRing());
        assertFalse(AlarmStatus.ESCALATED.shouldRing());
        assertFalse(AlarmStatus.CANCELLED.shouldRing());
        assertEquals(AlarmStatus.UNKNOWN, AlarmStatus.parse("NEW_STATE"));
    }

    private interface Call {
        void run() throws ApiException;
    }

    private static void expectValidation(Call call) {
        try {
            call.run();
            fail("검증 오류가 나야 합니다");
        } catch (ApiException e) {
            assertEquals("VALIDATION_FAILED", e.code);
        }
    }
}
