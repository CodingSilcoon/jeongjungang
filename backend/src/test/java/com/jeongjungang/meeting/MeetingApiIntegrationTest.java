package com.jeongjungang.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.jeongjungang.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 약속·초대 API를 실제 Postgres 위에서 끝까지 돌려 본다 (docs/API.md 6절). */
@AutoConfigureMockMvc
class MeetingApiIntegrationTest extends IntegrationTestSupport {

    private static final String BASE = "/api/v1";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    /** 만든 약속의 id·초대 코드와 참가자 하나의 id·토큰. */
    private record Member(String meetingId, String inviteCode, String participantId, String token) {
    }

    // ---- 만들기·미리보기·참가 ----

    @Test
    void create_returnsHostTokenInviteLinkAndExpiry() throws Exception {
        send(post(BASE + "/meetings"), """
                {"title":" 금요일 저녁 ","hostNickname":"민수","purpose":"MEAL",
                 "meetAt":"2026-12-10T19:00:00+09:00",
                 "origin":{"label":"홍대입구역","lat":37.5572,"lng":126.9245}}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.meeting.title").value("금요일 저녁"))
                .andExpect(jsonPath("$.data.meeting.status").value("OPEN"))
                .andExpect(jsonPath("$.data.meeting.inviteCode").value(org.hamcrest.Matchers.matchesPattern("[A-HJKMNP-TV-Z2-9]{8}")))
                .andExpect(jsonPath("$.data.meeting.inviteUrl").value(startsWith("http://localhost:8080/m/")))
                .andExpect(jsonPath("$.data.meeting.meetAt").value("2026-12-10T10:00:00Z"))
                .andExpect(jsonPath("$.data.meeting.expiresAt").value("2026-12-11T10:00:00Z"))
                .andExpect(jsonPath("$.data.meeting.accountability.marginMinutes").value(5))
                .andExpect(jsonPath("$.data.participant.role").value("HOST"))
                .andExpect(jsonPath("$.data.participantToken").isString());
    }

    @Test
    void create_rejectsInvalidNicknameWithFieldReason() throws Exception {
        send(post(BASE + "/meetings"), "{\"hostNickname\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields.hostNickname").exists());
        send(post(BASE + "/meetings"), "{\"hostNickname\":\"" + "가".repeat(21) + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_rejectsInvisibleCharactersInNickname() throws Exception {
        send(post(BASE + "/meetings"), "{\"hostNickname\":\"민‮수\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.hostNickname").exists());
        send(post(BASE + "/meetings"), "{\"hostNickname\":\"민​수\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void meetAt_mustBeWithinOneYearAndNotInThePast() throws Exception {
        String farFuture = Instant.now().plus(400, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
        String past = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString();

        send(post(BASE + "/meetings"), "{\"hostNickname\":\"민수\",\"meetAt\":\"" + farFuture + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.meetAt").exists());
        send(post(BASE + "/meetings"), "{\"hostNickname\":\"민수\",\"meetAt\":\"" + past + "\"}")
                .andExpect(status().isBadRequest());
        Member host = createMeeting("민수");
        authedSend(patch(BASE + "/meetings/" + host.meetingId()), host, "{\"meetAt\":\"" + farFuture + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void snapshot_isNotStoredByProxies() throws Exception {
        Member host = createMeeting("민수");
        snapshot(host).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("private")));
    }

    @Test
    void preview_acceptsLowercaseHyphenatedCode_andHidesLocations() throws Exception {
        Member host = createMeeting("민수");
        String messy = host.inviteCode().substring(0, 4).toLowerCase() + "-" + host.inviteCode().substring(4);

        mvc.perform(get(BASE + "/meetings/by-code/" + messy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hostNickname").value("민수"))
                .andExpect(jsonPath("$.data.participantCount").value(1))
                .andExpect(jsonPath("$.data.origin").doesNotExist());
    }

    @Test
    void unknownInviteCode_isMeetingNotFound() throws Exception {
        mvc.perform(get(BASE + "/meetings/by-code/ZZZZZZZZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEETING_NOT_FOUND"));
        mvc.perform(get(BASE + "/meetings/by-code/not-a-code"))
                .andExpect(jsonPath("$.error.code").value("MEETING_NOT_FOUND"));
    }

    @Test
    void join_isLimitedToTenParticipants() throws Exception {
        Member host = createMeeting("방장");
        for (int i = 2; i <= MeetingService.MAX_PARTICIPANTS; i++) {
            join(host, "멤버" + i);
        }
        send(post(BASE + "/meetings/by-code/" + host.inviteCode() + "/participants"), "{\"nickname\":\"열한번째\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MEETING_FULL"));
    }

    @Test
    void join_afterConfirmation_isMeetingClosed() throws Exception {
        Member host = createMeeting("방장");
        confirm(host);

        send(post(BASE + "/meetings/by-code/" + host.inviteCode() + "/participants"), "{\"nickname\":\"늦은사람\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MEETING_CLOSED"));
    }

    // ---- 조회·폴링 ----

    @Test
    void snapshot_listsParticipantsInJoinOrder_andSupportsEtag() throws Exception {
        Member host = createMeeting("민수");
        Member jihyun = join(host, "지현");
        join(host, "철수");

        String etag = authed(get(BASE + "/meetings/" + host.meetingId()), jihyun)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participants", hasSize(3)))
                .andExpect(jsonPath("$.data.participants[0].nickname").value("민수"))
                .andExpect(jsonPath("$.data.participants[0].origin.label").value("홍대입구역"))
                .andExpect(jsonPath("$.data.participants[1].nickname").value("지현"))
                .andExpect(jsonPath("$.data.participants[2].nickname").value("철수"))
                .andExpect(jsonPath("$.data.me.participantId").value(jihyun.participantId()))
                .andExpect(jsonPath("$.data.me.role").value("MEMBER"))
                .andExpect(jsonPath("$.data.serverTime").isString())
                .andExpect(header().exists("ETag"))
                .andReturn().getResponse().getHeader("ETag");

        authed(get(BASE + "/meetings/" + host.meetingId()).header("If-None-Match", etag), jihyun)
                .andExpect(status().isNotModified());

        join(host, "영희");
        authed(get(BASE + "/meetings/" + host.meetingId()).header("If-None-Match", etag), jihyun)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participants", hasSize(4)));
    }

    @Test
    void snapshot_requiresValidTokenOfThatMeeting() throws Exception {
        Member a = createMeeting("에이");
        Member b = createMeeting("비");

        mvc.perform(get(BASE + "/meetings/" + a.meetingId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(get(BASE + "/meetings/" + a.meetingId()).header("Authorization", "Bearer wrong"))
                .andExpect(status().isUnauthorized());
        authed(get(BASE + "/meetings/" + a.meetingId()), b)
                .andExpect(status().isForbidden());
    }

    @Test
    void expiredMeeting_isGone() throws Exception {
        Member host = createMeeting("민수");
        jdbc.update("update meetings set expires_at = ? where id = ?::uuid",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)), host.meetingId());

        authed(get(BASE + "/meetings/" + host.meetingId()), host)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("MEETING_EXPIRED"));
        mvc.perform(get(BASE + "/meetings/by-code/" + host.inviteCode()))
                .andExpect(status().isGone());
    }

    // ---- 약속 수정·취소 ----

    @Test
    void update_onlyHostMayChangeMeeting() throws Exception {
        Member host = createMeeting("방장");
        Member member = join(host, "멤버");

        authedSend(patch(BASE + "/meetings/" + host.meetingId()), member, "{\"title\":\"바꿈\"}")
                .andExpect(status().isForbidden());
        authedSend(patch(BASE + "/meetings/" + host.meetingId()), host, "{\"title\":\"바꿈\"}")
                .andExpect(status().isNoContent());
        snapshot(host).andExpect(jsonPath("$.data.meeting.title").value("바꿈"));
    }

    @Test
    void confirm_requiresPlaceAndTime() throws Exception {
        Member host = createMeeting("방장");

        authedSend(patch(BASE + "/meetings/" + host.meetingId()), host, "{\"status\":\"CONFIRMED\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields.place").exists())
                .andExpect(jsonPath("$.error.fields.meetAt").exists());

        confirm(host);
        snapshot(host)
                .andExpect(jsonPath("$.data.meeting.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.meeting.place.name").value("답십리역"))
                .andExpect(jsonPath("$.data.meeting.place.lines[0]").value(5));

        authedSend(patch(BASE + "/meetings/" + host.meetingId()), host, "{\"status\":\"OPEN\"}")
                .andExpect(status().isNoContent());
        snapshot(host).andExpect(jsonPath("$.data.meeting.status").value("OPEN"));
    }

    @Test
    void cancel_deletesMeeting_andTokensStopWorking() throws Exception {
        Member host = createMeeting("방장");
        Member member = join(host, "멤버");

        authed(delete(BASE + "/meetings/" + host.meetingId()), member).andExpect(status().isForbidden());
        authed(delete(BASE + "/meetings/" + host.meetingId()), host).andExpect(status().isNoContent());

        authed(get(BASE + "/meetings/" + host.meetingId()), member).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/meetings/by-code/" + host.inviteCode())).andExpect(status().isNotFound());
    }

    // ---- 참가자 수정·나가기 ----

    @Test
    void updateParticipant_onlySelf() throws Exception {
        Member host = createMeeting("방장");
        Member member = join(host, "멤버");
        Member stranger = createMeeting("남");

        authedSend(patch(BASE + "/participants/" + member.participantId()), member,
                "{\"nickname\":\" 새이름 \",\"prepMinutes\":40,\"optedIn\":true,"
                        + "\"origin\":{\"label\":\"노원역\",\"lat\":37.6552,\"lng\":127.0614}}")
                .andExpect(status().isNoContent());
        snapshot(host)
                .andExpect(jsonPath("$.data.participants[1].nickname").value("새이름"))
                .andExpect(jsonPath("$.data.participants[1].prepMinutes").value(40))
                .andExpect(jsonPath("$.data.participants[1].optedIn").value(true))
                .andExpect(jsonPath("$.data.participants[1].origin.label").value("노원역"));

        authedSend(patch(BASE + "/participants/" + member.participantId()), host, "{\"nickname\":\"남이바꿈\"}")
                .andExpect(status().isForbidden());
        authedSend(patch(BASE + "/participants/" + member.participantId()), stranger, "{\"nickname\":\"남이바꿈\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PARTICIPANT_NOT_FOUND"));
        authedSend(patch(BASE + "/participants/" + member.participantId()), member, "{\"prepMinutes\":241}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void hostLeaving_handsHostToEarliestJoiner() throws Exception {
        Member host = createMeeting("방장");
        Member first = join(host, "첫째");
        Member second = join(host, "둘째");

        authed(delete(BASE + "/participants/" + host.participantId()), host).andExpect(status().isNoContent());

        snapshot(second)
                .andExpect(jsonPath("$.data.participants", hasSize(2)))
                .andExpect(jsonPath("$.data.participants[0].id").value(first.participantId()))
                .andExpect(jsonPath("$.data.participants[0].role").value("HOST"))
                .andExpect(jsonPath("$.data.participants[1].role").value("MEMBER"));
        authedSend(patch(BASE + "/meetings/" + host.meetingId()), first, "{\"title\":\"새 방장이 바꿈\"}")
                .andExpect(status().isNoContent());
        authed(get(BASE + "/meetings/" + host.meetingId()), host).andExpect(status().isUnauthorized());
    }

    @Test
    void hostLeavingAlone_cancelsMeeting() throws Exception {
        Member host = createMeeting("혼자");

        authed(delete(BASE + "/participants/" + host.participantId()), host).andExpect(status().isNoContent());

        mvc.perform(get(BASE + "/meetings/by-code/" + host.inviteCode())).andExpect(status().isNotFound());
    }

    @Test
    void hostMayKickMembers_membersMayNotKickOthers() throws Exception {
        Member host = createMeeting("방장");
        Member a = join(host, "에이");
        Member b = join(host, "비");

        authed(delete(BASE + "/participants/" + b.participantId()), a).andExpect(status().isForbidden());
        authed(delete(BASE + "/participants/" + b.participantId()), host).andExpect(status().isNoContent());

        authed(get(BASE + "/meetings/" + host.meetingId()), b).andExpect(status().isUnauthorized());
        snapshot(host).andExpect(jsonPath("$.data.participants[0].role").value("HOST"));
        authed(delete(BASE + "/participants/" + a.participantId()), a).andExpect(status().isNoContent());
        snapshot(host).andExpect(jsonPath("$.data.participants", hasSize(1)));
    }

    // ---- 도우미 ----

    private Member createMeeting(String nickname) throws Exception {
        String body = "{\"hostNickname\":\"" + nickname + "\","
                + "\"origin\":{\"label\":\"홍대입구역\",\"lat\":37.5572,\"lng\":126.9245}}";
        return toMember(send(post(BASE + "/meetings"), body).andExpect(status().isCreated()));
    }

    private Member join(Member host, String nickname) throws Exception {
        String json = send(post(BASE + "/meetings/by-code/" + host.inviteCode() + "/participants"),
                "{\"nickname\":\"" + nickname + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.meetingId").value(host.meetingId()))
                .andExpect(jsonPath("$.data.participant.role").value("MEMBER"))
                .andReturn().getResponse().getContentAsString();
        return new Member(host.meetingId(), host.inviteCode(),
                JsonPath.read(json, "$.data.participant.id"), JsonPath.read(json, "$.data.participantToken"));
    }

    private void confirm(Member host) throws Exception {
        authedSend(patch(BASE + "/meetings/" + host.meetingId()), host, """
                {"meetAt":"2026-12-10T19:00:00+09:00",
                 "place":{"name":"답십리역","lat":37.5669,"lng":127.0527,"lines":[5]},
                 "status":"CONFIRMED"}""")
                .andExpect(status().isNoContent());
    }

    private ResultActions snapshot(Member who) throws Exception {
        return authed(get(BASE + "/meetings/" + who.meetingId()), who).andExpect(status().isOk());
    }

    private Member toMember(ResultActions created) throws Exception {
        String json = created.andReturn().getResponse().getContentAsString();
        Member member = new Member(JsonPath.read(json, "$.data.meeting.id"), JsonPath.read(json, "$.data.meeting.inviteCode"),
                JsonPath.read(json, "$.data.participant.id"), JsonPath.read(json, "$.data.participantToken"));
        assertThat(member.token()).isNotBlank();
        return member;
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions authed(MockHttpServletRequestBuilder request, Member who) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + who.token()));
    }

    private ResultActions authedSend(MockHttpServletRequestBuilder request, Member who, String body) throws Exception {
        return authed(request.contentType(MediaType.APPLICATION_JSON).content(body), who);
    }
}
