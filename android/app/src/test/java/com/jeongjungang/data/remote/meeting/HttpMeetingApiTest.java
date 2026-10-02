package com.jeongjungang.data.remote.meeting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.jeongjungang.data.remote.ApiException;
import com.jeongjungang.data.remote.ApiHttp;
import com.jeongjungang.data.remote.meeting.MeetingModels.InvitePreview;
import com.jeongjungang.data.remote.meeting.MeetingModels.Member;
import com.jeongjungang.data.remote.meeting.MeetingModels.Membership;
import com.jeongjungang.data.remote.meeting.MeetingModels.Origin;
import com.jeongjungang.data.remote.meeting.MeetingModels.Place;
import com.jeongjungang.data.remote.meeting.MeetingModels.PlacePage;
import com.jeongjungang.data.remote.meeting.MeetingModels.Purpose;
import com.jeongjungang.data.remote.meeting.MeetingModels.Role;
import com.jeongjungang.data.remote.meeting.MeetingModels.Snapshot;
import com.jeongjungang.data.remote.meeting.MeetingModels.Status;
import com.jeongjungang.domain.model.LatLng;
import com.jeongjungang.testing.FakeServer;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** docs/API.md 6절 예시 응답 그대로 요청·응답 변환을 확인한다. */
public class HttpMeetingApiTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final long MEET_AT = OffsetDateTime.parse("2026-10-10T19:00:00+09:00").toInstant().toEpochMilli();
    private static final Origin HONGDAE = new Origin("홍대입구역", new LatLng(37.5572, 126.9245));

    private FakeServer server;
    private HttpMeetingApi api;

    @Before
    public void start() throws IOException {
        server = new FakeServer();
        api = new HttpMeetingApi(new ApiHttp(server.baseUrl()), SEOUL);
    }

    @After
    public void stop() throws IOException {
        server.close();
    }

    private static String ok(String data) {
        return "{\"success\":true,\"data\":" + data + ",\"error\":null}";
    }

    private static String error(String code, String message) {
        return "{\"success\":false,\"data\":null,\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}}";
    }

    @Test
    public void createSendsSpecBodyAndReadsHostMembership() throws Exception {
        server.enqueue(201, ok("{\"meeting\":{\"id\":\"m-1\",\"inviteCode\":\"7K3QH9MX\","
                + "\"inviteUrl\":\"https://jjg.example/m/7K3QH9MX\",\"title\":\"금요일 저녁\",\"purpose\":\"MEAL\","
                + "\"meetAt\":\"2026-10-10T19:00:00+09:00\",\"status\":\"OPEN\",\"place\":null,"
                + "\"expiresAt\":\"2026-10-11T19:00:00+09:00\"},"
                + "\"participant\":{\"id\":\"p-1\",\"nickname\":\"민수\",\"role\":\"HOST\"},\"participantToken\":\"tok\"}"));

        Membership m = api.create(" 민수 ", "금요일 저녁", Purpose.MEAL, MEET_AT, HONGDAE);

        FakeServer.Request req = server.last();
        assertEquals("POST", req.method);
        assertEquals("/api/v1/meetings", req.target);
        assertNull(req.header("Authorization"));
        JSONObject body = new JSONObject(req.body);
        assertEquals("민수", body.getString("hostNickname"));
        assertEquals("금요일 저녁", body.getString("title"));
        assertEquals("MEAL", body.getString("purpose"));
        assertEquals("2026-10-10T19:00:00+09:00", body.getString("meetAt"));
        assertEquals("홍대입구역", body.getJSONObject("origin").getString("label"));
        assertEquals(37.5572, body.getJSONObject("origin").getDouble("lat"), 1e-9);

        assertEquals("m-1", m.meetingId);
        assertEquals("p-1", m.participantId);
        assertEquals(Role.HOST, m.role);
        assertEquals("tok", m.token);
        assertEquals("https://jjg.example/m/7K3QH9MX", m.meeting.inviteUrl);
        assertEquals(Long.valueOf(MEET_AT), m.meeting.meetAtMillis);
        assertEquals(Status.OPEN, m.meeting.status);
        assertNull(m.meeting.place);
    }

    @Test
    public void createOmitsOptionalFields() throws Exception {
        server.enqueue(201, ok("{\"meeting\":{\"id\":\"m-1\",\"status\":\"OPEN\"},"
                + "\"participant\":{\"id\":\"p-1\",\"nickname\":\"민수\",\"role\":\"HOST\"},\"participantToken\":\"t\"}"));
        api.create("민수", "  ", null, null, null);
        JSONObject body = new JSONObject(server.last().body);
        assertEquals(1, body.length());
        assertTrue(body.has("hostNickname"));
    }

    @Test
    public void invalidInputIsRejectedBeforeCallingServer() {
        expectValidation(() -> api.create("", null, null, null, null));
        expectValidation(() -> api.create("이름이스물한글자이상이면안되는이름입니다요", null, null, null, null));
        expectValidation(() -> api.join("short", "지현", null));
        expectValidation(() -> api.preview("7K3QH9M0")); // 0은 쓰지 않는 글자
        assertTrue(server.requests().isEmpty());
    }

    @Test
    public void previewAndJoinAcceptPastedLink() throws Exception {
        server.enqueue(200, ok("{\"title\":\"금요일 저녁\",\"hostNickname\":\"민수\",\"participantCount\":2,"
                + "\"status\":\"OPEN\",\"meetAt\":\"2026-10-10T19:00:00+09:00\"}"));
        InvitePreview p = api.preview("https://jjg.example/m/7k3q-h9mx");
        assertEquals("/api/v1/meetings/by-code/7K3QH9MX", server.last().target);
        assertEquals("민수", p.hostNickname);
        assertEquals(2, p.participantCount);

        server.enqueue(201, ok("{\"participant\":{\"id\":\"p-2\",\"nickname\":\"지현\",\"role\":\"MEMBER\"},"
                + "\"participantToken\":\"tok2\",\"meetingId\":\"m-1\"}"));
        Membership m = api.join("7K3QH9MX", "지현", null);
        assertEquals("/api/v1/meetings/by-code/7K3QH9MX/participants", server.last().target);
        assertEquals("{\"nickname\":\"지현\"}", server.last().body);
        assertEquals("m-1", m.meetingId);
        assertEquals(Role.MEMBER, m.role);
        assertNull(m.meeting);
    }

    @Test
    public void joinErrorsPassServerMessage() {
        server.enqueue(409, error("MEETING_FULL", "약속 인원이 다 찼어요."));
        try {
            api.join("7K3QH9MX", "지현", null);
            fail();
        } catch (ApiException e) {
            assertEquals("MEETING_FULL", e.code);
            assertEquals("약속 인원이 다 찼어요.", e.getMessage());
        }
    }

    @Test
    public void getParsesSnapshotAndSendsTokenAndEtag() throws Exception {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("ETag", "\"v12\"");
        server.enqueue(200, ok("{\"version\":12,\"meeting\":{\"id\":\"m-1\",\"title\":\"금요일 저녁\",\"purpose\":\"MEAL\","
                + "\"meetAt\":\"2026-10-10T19:00:00+09:00\",\"status\":\"CONFIRMED\","
                + "\"place\":{\"name\":\"답십리역\",\"lat\":37.5669,\"lng\":127.0527,\"lines\":[5]},"
                + "\"accountability\":{\"enabled\":false,\"gracePeriodSec\":60,\"marginMinutes\":0}},"
                + "\"participants\":["
                + "{\"id\":\"p-1\",\"nickname\":\"민수\",\"role\":\"HOST\",\"origin\":{\"label\":\"홍대입구역\",\"lat\":37.5572,\"lng\":126.9245},\"prepMinutes\":null,\"optedIn\":false},"
                + "{\"id\":\"p-2\",\"nickname\":\"지현\",\"role\":\"MEMBER\",\"origin\":null,\"prepMinutes\":40,\"optedIn\":true}],"
                + "\"me\":{\"participantId\":\"p-2\",\"role\":\"MEMBER\"},\"serverTime\":\"2026-10-10T10:00:00Z\"}"), headers);

        Snapshot s = api.get("m-1", "tok2", null);

        FakeServer.Request req = server.last();
        assertEquals("GET", req.method);
        assertEquals("/api/v1/meetings/m-1", req.target);
        assertEquals("Bearer tok2", req.header("Authorization"));
        assertNull(req.header("If-None-Match"));

        assertEquals(12, s.version);
        assertEquals("\"v12\"", s.etag);
        assertEquals(Status.CONFIRMED, s.meeting.status);
        assertEquals("답십리역", s.meeting.place.name);
        assertEquals(Collections.singletonList(5), s.meeting.place.lines);
        assertFalse(s.meeting.accountability.enabled);
        assertEquals(2, s.members.size());
        Member host = s.members.get(0);
        assertEquals(Role.HOST, host.role);
        assertNull(host.prepMinutes);
        Member me = s.me();
        assertEquals("지현", me.nickname);
        assertNull(me.origin);
        assertEquals(Integer.valueOf(40), me.prepMinutes);
        assertTrue(me.optedIn);
        assertFalse(s.amHost());
        assertEquals(1, s.recommendParticipants().size());
        assertEquals("민수", s.recommendParticipants().get(0).name);
        assertEquals(1, s.membersWithoutOrigin());
        assertEquals(OffsetDateTime.parse("2026-10-10T10:00:00Z").toInstant().toEpochMilli(), s.serverTimeMillis);
    }

    @Test
    public void notModifiedReturnsNull() throws Exception {
        server.enqueue(304, "");
        assertNull(api.get("m-1", "tok", "\"v12\""));
        assertEquals("\"v12\"", server.last().header("If-None-Match"));
    }

    @Test
    public void updateSendsOnlySetFieldsWithPatch() throws Exception {
        server.enqueue(200, ok("{}"));
        Place place = new Place("답십리역", new LatLng(37.5669, 127.0527), Arrays.asList(5));
        api.update("m-1", "tok", new MeetingUpdate().place(place).meetAt(MEET_AT).confirm());

        FakeServer.Request req = server.last();
        assertEquals("PATCH", req.method);
        assertEquals("/api/v1/meetings/m-1", req.target);
        assertEquals("Bearer tok", req.header("Authorization"));
        JSONObject body = new JSONObject(req.body);
        assertEquals(3, body.length());
        assertEquals("CONFIRMED", body.getString("status"));
        assertEquals("2026-10-10T19:00:00+09:00", body.getString("meetAt"));
        assertEquals(5, body.getJSONObject("place").getJSONArray("lines").getInt(0));
    }

    @Test
    public void emptyUpdatesDoNotCallServer() throws Exception {
        api.update("m-1", "tok", new MeetingUpdate());
        api.updateParticipant("p-1", "tok", new ParticipantUpdate());
        assertTrue(server.requests().isEmpty());
    }

    @Test
    public void participantUpdateAndValidation() throws Exception {
        server.enqueue(200, ok("{}"));
        api.updateParticipant("p-2", "tok", new ParticipantUpdate().origin(HONGDAE).prepMinutes(40).optedIn(true));
        FakeServer.Request req = server.last();
        assertEquals("PATCH", req.method);
        assertEquals("/api/v1/participants/p-2", req.target);
        JSONObject body = new JSONObject(req.body);
        assertEquals(40, body.getInt("prepMinutes"));
        assertTrue(body.getBoolean("optedIn"));
        assertFalse(body.has("nickname"));

        expectValidation(() -> api.updateParticipant("p-2", "tok", new ParticipantUpdate().prepMinutes(241)));
    }

    @Test
    public void deletesReturnNoContent() throws Exception {
        server.enqueue(204, "");
        api.removeParticipant("p-1", "tok");
        assertEquals("DELETE", server.last().method);
        assertEquals("/api/v1/participants/p-1", server.last().target);

        server.enqueue(204, "");
        api.cancel("m-1", "tok");
        assertEquals("/api/v1/meetings/m-1", server.last().target);
    }

    @Test
    public void idsAreEncodedAsSinglePathSegment() throws Exception {
        server.enqueue(204, "");
        api.cancel("a/b c", "tok");
        assertEquals("/api/v1/meetings/a%2Fb%20c", server.last().target);
    }

    @Test
    public void placesQueryAndPaging() throws Exception {
        server.enqueue(200, ok("{\"items\":[{\"name\":\"○○식당\",\"category\":\"한식\",\"address\":\"서울 동대문구\","
                + "\"lat\":37.567,\"lng\":127.053,\"distanceMeters\":180,\"placeUrl\":\"https://place.map.kakao.com/1\"}],"
                + "\"page\":2,\"hasNext\":true}"));
        PlacePage page = api.places(new LatLng(37.5669, 127.0527), Purpose.MEAL.placeCategory, 5000, 2);
        assertEquals("/api/v1/places?lat=37.566900&lng=127.052700&category=FOOD&radius=1000&page=2",
                server.last().target);
        assertEquals(1, page.items.size());
        assertEquals(180, page.items.get(0).distanceMeters);
        assertEquals(2, page.page);
        assertTrue(page.hasNext);
    }

    @Test
    public void purposeMapsToPlaceCategory() {
        assertEquals("FOOD", Purpose.MEAL.placeCategory);
        assertEquals("CAFE", Purpose.CAFE.placeCategory);
        assertEquals("BAR", Purpose.DRINK.placeCategory);
        assertNull(Purpose.ETC.placeCategory);
        expectValidation(() -> api.places(new LatLng(37, 127), null, 500, 1));
    }

    @Test
    public void unknownEnumValuesDoNotCrash() throws Exception {
        server.enqueue(200, ok("{\"title\":null,\"hostNickname\":\"민수\",\"participantCount\":1,\"status\":\"ARCHIVED\"}"));
        InvitePreview p = api.preview("7K3QH9MX");
        assertEquals(Status.UNKNOWN, p.status);
        assertNull(p.title);
        assertNull(p.meetAtMillis);
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
