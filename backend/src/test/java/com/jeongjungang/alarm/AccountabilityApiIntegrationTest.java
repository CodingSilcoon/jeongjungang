package com.jeongjungang.alarm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.jeongjungang.IntegrationTestSupport;
import com.jeongjungang.push.DeviceService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 책임 알람 API와 에스컬레이션을 실제 Postgres·Redis 위에서 끝까지 돌려 본다 (docs/API.md 7·8절). */
@AutoConfigureMockMvc
class AccountabilityApiIntegrationTest extends IntegrationTestSupport {

    private static final String BASE = "/api/v1";
    private static final String PLACE = "답십리역";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EscalationScheduler scheduler;

    @Autowired
    private EscalationQueue queue;

    @Autowired
    private DeviceService devices;

    @Autowired
    private EscalationService escalation;

    @Autowired
    private StringRedisTemplate redis;

    private record Member(String meetingId, String inviteCode, String participantId, String token) {
    }

    // ---- 켜기 조건 ----

    @Test
    void enable_requiresEveryoneOptedIn() throws Exception {
        Member host = createMeeting("민수");
        Member member = join(host, "지현");
        optIn(host, 30);
        confirm(host, meetAt());

        enable(host).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NOT_ALL_OPTED_IN"));
        optIn(member, 20);
        enable(host).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.gracePeriodSec").value(60))
                .andExpect(jsonPath("$.data.marginMinutes").value(5));
    }

    @Test
    void enable_requiresPlaceAndTime() throws Exception {
        Member host = createMeeting("민수");
        optIn(host, 30);

        enable(host).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALARM_NOT_READY"));
    }

    @Test
    void enable_isHostOnly_andValidatesGracePeriod() throws Exception {
        Member host = createMeeting("민수");
        Member member = join(host, "지현");
        optIn(host, 30);
        optIn(member, 30);
        confirm(host, meetAt());

        enable(member).andExpect(status().isForbidden());
        authedSend(post(BASE + "/meetings/" + host.meetingId() + "/accountability"), host,
                "{\"gracePeriodSec\":10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.gracePeriodSec").exists());
        authedSend(post(BASE + "/meetings/" + host.meetingId() + "/accountability"), host,
                "{\"gracePeriodSec\":120,\"marginMinutes\":10}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gracePeriodSec").value(120))
                .andExpect(jsonPath("$.data.marginMinutes").value(10));
    }

    // ---- 이동시간 보고와 알람 만들기 ----

    @Test
    void travel_mustMatchConfirmedPlace_andOnlyForSelf() throws Exception {
        Member host = createMeeting("민수");
        Member member = join(host, "지현");
        confirm(host, meetAt());

        travel(member, "청량리역", 20).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALARM_NOT_READY"));
        authedSend(put(BASE + "/participants/" + host.participantId() + "/travel"), member,
                travelBody(PLACE, 20)).andExpect(status().isForbidden());
        travel(member, PLACE, 20).andExpect(status().isNoContent());
    }

    @Test
    void alarm_isCreatedOnceTravelAndPrepAreKnown_withServerComputedFireAt() throws Exception {
        Instant meetAt = meetAt();
        Member host = readyMeeting(meetAt);
        registerDevice(host, "host-device");

        myAlarm(host).andExpect(jsonPath("$.data.alarm").isEmpty())
                .andExpect(jsonPath("$.data.accountability.enabled").value(true));

        travel(host, PLACE, 26.4).andExpect(status().isNoContent());

        // 약속 − 27분(올림) − 준비 30분 − 여유 5분
        Instant fireAt = meetAt.minus(Duration.ofMinutes(62));
        myAlarm(host)
                .andExpect(jsonPath("$.data.alarm.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.alarm.fireAt").value(fireAt.toString()))
                .andExpect(jsonPath("$.data.alarm.graceUntil").value(fireAt.plusSeconds(60).toString()))
                .andExpect(jsonPath("$.data.serverTime").exists());
        assertThat(pushes.tokensOf("ALARM_SYNC")).containsExactly("host-device");
        assertThat(queueScore(alarmId(host))).isEqualTo(fireAt.plusSeconds(60).toEpochMilli());
    }

    @Test
    void changingMeetAt_movesTheAlarm_changingPlace_cancelsUntilNewTravel() throws Exception {
        Instant meetAt = meetAt();
        Member host = readyMeeting(meetAt);
        travel(host, PLACE, 20);
        registerDevice(host, "host-device");
        pushes.clear();

        Instant later = meetAt.plus(Duration.ofHours(1));
        authedSend(patch(BASE + "/meetings/" + host.meetingId()), host,
                "{\"meetAt\":\"" + later + "\"}").andExpect(status().isNoContent());
        myAlarm(host).andExpect(jsonPath("$.data.alarm.fireAt")
                .value(later.minus(Duration.ofMinutes(55)).toString()));
        assertThat(pushes.tokensOf("ALARM_SYNC")).containsExactly("host-device");

        authedSend(patch(BASE + "/meetings/" + host.meetingId()), host,
                "{\"place\":{\"name\":\"청량리역\",\"lat\":37.58,\"lng\":127.04,\"lines\":[1]}}")
                .andExpect(status().isNoContent());
        myAlarm(host).andExpect(jsonPath("$.data.alarm.status").value("CANCELLED"));
        assertThat(pushes.tokensOf("ALARM_CANCELLED")).containsExactly("host-device");

        travel(host, "청량리역", 15);
        myAlarm(host).andExpect(jsonPath("$.data.alarm.status").value("SCHEDULED"));
    }

    @Test
    void alarmWhoseTimeAlreadyPassed_isNotCreated() throws Exception {
        Instant soon = Instant.now().plus(Duration.ofMinutes(20)).truncatedTo(ChronoUnit.MINUTES);
        Member host = readyMeeting(soon);

        travel(host, PLACE, 30);

        myAlarm(host).andExpect(jsonPath("$.data.alarm").isEmpty());
    }

    @Test
    void optingOut_cancelsOnlyThatPersonsAlarm() throws Exception {
        Member host = createMeeting("민수");
        Member member = join(host, "지현");
        optIn(host, 30);
        optIn(member, 30);
        confirm(host, meetAt());
        enable(host).andExpect(status().isOk());
        travel(host, PLACE, 20);
        travel(member, PLACE, 20);

        authedSend(patch(BASE + "/participants/" + member.participantId()), member, "{\"optedIn\":false}")
                .andExpect(status().isNoContent());

        myAlarm(member).andExpect(jsonPath("$.data.alarm.status").value("CANCELLED"));
        myAlarm(host).andExpect(jsonPath("$.data.alarm.status").value("SCHEDULED"));
    }

    // ---- 울림·끔 ----

    @Test
    void dismiss_isIdempotent() throws Exception {
        Member host = readyMeeting(meetAt());
        travel(host, PLACE, 20);
        String alarmId = alarmId(host);

        authed(post(BASE + "/alarms/" + alarmId + "/ringing"), host).andExpect(status().isNoContent());
        myAlarm(host).andExpect(jsonPath("$.data.alarm.status").value("RINGING"));
        for (int i = 0; i < 2; i++) {
            dismiss(host, alarmId).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DISMISSED"));
        }
    }

    @Test
    void optingOutAndBackIn_doesNotReArmADismissedAlarm() throws Exception {
        Member host = readyMeeting(meetAt());
        travel(host, PLACE, 20);
        String alarmId = alarmId(host);
        dismiss(host, alarmId).andExpect(status().isOk());

        authedSend(patch(BASE + "/participants/" + host.participantId()), host, "{\"optedIn\":false}")
                .andExpect(status().isNoContent());
        authedSend(patch(BASE + "/participants/" + host.participantId()), host, "{\"optedIn\":true}")
                .andExpect(status().isNoContent());

        myAlarm(host).andExpect(jsonPath("$.data.alarm.status").value("DISMISSED"));
    }

    @Test
    void someoneElsesAlarm_isNotFound() throws Exception {
        Member host = readyMeeting(meetAt());
        travel(host, PLACE, 20);
        Member stranger = readyMeeting(meetAt());

        dismiss(stranger, alarmId(host)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ALARM_NOT_FOUND"));
        authed(post(BASE + "/alarms/" + UUID.randomUUID() + "/ringing"), host)
                .andExpect(status().isNotFound());
    }

    // ---- 에스컬레이션 ----

    @Test
    void overdueAlarm_escalatesOnceToOtherOptedInMembers() throws Exception {
        Member host = createMeeting("민수");
        Member sleeper = join(host, "지현");
        optIn(host, 30);
        optIn(sleeper, 30);
        confirm(host, meetAt());
        enable(host).andExpect(status().isOk());
        travel(sleeper, PLACE, 20);
        registerDevice(host, "host-device");
        registerDevice(sleeper, "sleeper-device");
        String alarmId = alarmId(sleeper);
        makeOverdue(alarmId);
        pushes.clear();

        scheduler.poll();

        myAlarm(sleeper).andExpect(jsonPath("$.data.alarm.status").value("ESCALATED"));
        assertThat(pushes.tokensOf("ESCALATION")).containsExactly("host-device");
        assertThat(pushes.sent().get(0).data())
                .containsEntry("sleeperNickname", "지현")
                .containsEntry("alarmId", alarmId)
                .containsKeys("fireAt", "escalatedAt");
        assertThat(pushes.sent().get(0).ttl()).isEqualTo(Duration.ofSeconds(120));

        // 큐에 다시 들어와도 두 번 보내지 않고, 늦게 온 끔 보고는 되돌리지 않는다
        queue.add(UUID.fromString(alarmId), Instant.now().minusSeconds(1));
        scheduler.poll();
        assertThat(pushes.tokensOf("ESCALATION")).hasSize(1);
        dismiss(sleeper, alarmId).andExpect(jsonPath("$.data.status").value("ESCALATED"));
    }

    @Test
    void dismissedAlarm_doesNotEscalate() throws Exception {
        Member host = readyMeeting(meetAt());
        travel(host, PLACE, 20);
        String alarmId = alarmId(host);
        dismiss(host, alarmId).andExpect(status().isOk());
        makeOverdue(alarmId);

        assertThat(escalation.escalate(UUID.fromString(alarmId))).isFalse();
        myAlarm(host).andExpect(jsonPath("$.data.alarm.status").value("DISMISSED"));
    }

    @Test
    void dismissArrivingAfterGrace_escalatesInsteadOfWinning() throws Exception {
        Member host = createMeeting("민수");
        Member sleeper = join(host, "지현");
        optIn(host, 30);
        optIn(sleeper, 30);
        confirm(host, meetAt());
        enable(host).andExpect(status().isOk());
        travel(sleeper, PLACE, 20);
        registerDevice(host, "host-device");
        String alarmId = alarmId(sleeper);
        makeOverdue(alarmId); // 주기 작업이 아직 못 본 상태

        dismiss(sleeper, alarmId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ESCALATED"));

        assertThat(pushes.tokensOf("ESCALATION")).containsExactly("host-device");
        scheduler.poll();
        assertThat(pushes.tokensOf("ESCALATION")).hasSize(1);
    }

    @Test
    void notYetDueAlarm_isPutBackInTheQueue() throws Exception {
        Member host = readyMeeting(meetAt());
        travel(host, PLACE, 20);
        String alarmId = alarmId(host);
        queue.add(UUID.fromString(alarmId), Instant.now().minusSeconds(1)); // 오래된 기한이 남아 있던 경우

        scheduler.poll();

        myAlarm(host).andExpect(jsonPath("$.data.alarm.status").value("SCHEDULED"));
        assertThat(queueScore(alarmId)).isGreaterThan(Instant.now().toEpochMilli());
    }

    @Test
    void reseed_refillsQueueFromDatabase() throws Exception {
        Member host = readyMeeting(meetAt());
        travel(host, PLACE, 20);
        String alarmId = alarmId(host);
        Instant graceUntil = Instant.parse(JsonPath.read(
                myAlarm(host).andReturn().getResponse().getContentAsString(), "$.data.alarm.graceUntil"));
        redis.delete(EscalationQueue.KEY); // Redis가 재시작돼 큐가 빈 경우

        escalation.reseedQueue();

        assertThat(queueScore(alarmId)).isEqualTo(graceUntil.toEpochMilli());
    }

    @Test
    void queue_keepsTheEarlierDeadline_soAStaleLaterScoreCannotDelayEscalation() throws Exception {
        Member host = readyMeeting(meetAt());
        travel(host, PLACE, 20);
        String alarmId = alarmId(host);
        long real = queueScore(alarmId);

        queue.add(UUID.fromString(alarmId), Instant.ofEpochMilli(real).plus(Duration.ofHours(3)));

        assertThat(queueScore(alarmId)).isEqualTo(real);
    }

    // ---- 끄기·나가기·취소 ----

    @Test
    void disable_cancelsEveryAlarm() throws Exception {
        Member host = readyMeeting(meetAt());
        travel(host, PLACE, 20);
        registerDevice(host, "host-device");

        authed(delete(BASE + "/meetings/" + host.meetingId() + "/accountability"), host)
                .andExpect(status().isNoContent());

        myAlarm(host).andExpect(jsonPath("$.data.accountability.enabled").value(false))
                .andExpect(jsonPath("$.data.alarm.status").value("CANCELLED"));
        assertThat(pushes.tokensOf("ALARM_CANCELLED")).containsExactly("host-device");
    }

    @Test
    void leaving_sendsCancelToTheLeaver_andCancellingMeeting_toEveryone() throws Exception {
        Member host = createMeeting("민수");
        Member member = join(host, "지현");
        optIn(host, 30);
        optIn(member, 30);
        confirm(host, meetAt());
        enable(host).andExpect(status().isOk());
        travel(host, PLACE, 20);
        travel(member, PLACE, 20);
        registerDevice(host, "host-device");
        registerDevice(member, "member-device");
        String memberAlarm = alarmId(member);
        pushes.clear();

        authed(delete(BASE + "/participants/" + member.participantId()), member).andExpect(status().isNoContent());
        assertThat(pushes.tokensOf("ALARM_CANCELLED")).containsExactly("member-device");
        assertThat(jdbc.queryForObject("select count(*) from alarms where id = ?::uuid", Integer.class, memberAlarm))
                .isZero();

        pushes.clear();
        authed(delete(BASE + "/meetings/" + host.meetingId()), host).andExpect(status().isNoContent());
        assertThat(pushes.tokensOf("ALARM_CANCELLED")).containsExactly("host-device");
    }

    // ---- 기기 ----

    @Test
    void registerDevice_upsertsOnePerParticipant_andValidates() throws Exception {
        Member host = createMeeting("민수");

        registerDevice(host, "first");
        registerDevice(host, "second");

        assertThat(jdbc.queryForObject("select fcm_token from devices where participant_id = ?::uuid",
                String.class, host.participantId())).isEqualTo("second");
        authedSend(post(BASE + "/devices"), host, "{\"fcmToken\":\"x\",\"platform\":\"IOS\"}")
                .andExpect(status().isBadRequest());
        authedSend(post(BASE + "/devices"), host, "{\"fcmToken\":\" \",\"platform\":\"ANDROID\"}")
                .andExpect(status().isBadRequest());
        send(post(BASE + "/devices"), "{\"fcmToken\":\"x\",\"platform\":\"ANDROID\"}")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void removeByFcmToken_removesEveryParticipantOnThatPhone() throws Exception {
        Member host = createMeeting("민수");
        Member member = join(host, "지현");
        Member other = createMeeting("서연");
        registerDevice(host, "same-phone");
        registerDevice(other, "same-phone");
        registerDevice(member, "member-phone");

        devices.removeByFcmToken("same-phone");

        assertThat(jdbc.queryForList("select fcm_token from devices where participant_id in (?::uuid, ?::uuid, ?::uuid)",
                String.class, host.participantId(), other.participantId(), member.participantId()))
                .containsExactly("member-phone");
    }

    // ---- 도우미 ----

    /** 내일모레 정각. 1년 제한 안이고, 알람시각이 지나 있지 않다. */
    private static Instant meetAt() {
        return Instant.now().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.HOURS);
    }

    /** 방장 혼자, 동의·준비 30분, 장소·시간 확정, 책임 알람 켬. 이동시간만 보고하면 알람이 생긴다. */
    private Member readyMeeting(Instant meetAt) throws Exception {
        Member host = createMeeting("민수");
        optIn(host, 30);
        confirm(host, meetAt);
        enable(host).andExpect(status().isOk());
        return host;
    }

    private Member createMeeting(String nickname) throws Exception {
        String json = send(post(BASE + "/meetings"), "{\"hostNickname\":\"" + nickname + "\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Member(JsonPath.read(json, "$.data.meeting.id"), JsonPath.read(json, "$.data.meeting.inviteCode"),
                JsonPath.read(json, "$.data.participant.id"), JsonPath.read(json, "$.data.participantToken"));
    }

    private Member join(Member host, String nickname) throws Exception {
        String json = send(post(BASE + "/meetings/by-code/" + host.inviteCode() + "/participants"),
                "{\"nickname\":\"" + nickname + "\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Member(host.meetingId(), host.inviteCode(),
                JsonPath.read(json, "$.data.participant.id"), JsonPath.read(json, "$.data.participantToken"));
    }

    private void optIn(Member who, int prepMinutes) throws Exception {
        authedSend(patch(BASE + "/participants/" + who.participantId()), who,
                "{\"prepMinutes\":" + prepMinutes + ",\"optedIn\":true}")
                .andExpect(status().isNoContent());
    }

    private void confirm(Member host, Instant meetAt) throws Exception {
        authedSend(patch(BASE + "/meetings/" + host.meetingId()), host, "{\"meetAt\":\"" + meetAt + "\","
                + "\"place\":{\"name\":\"" + PLACE + "\",\"lat\":37.5669,\"lng\":127.0527,\"lines\":[5]},"
                + "\"status\":\"CONFIRMED\"}")
                .andExpect(status().isNoContent());
    }

    private ResultActions enable(Member who) throws Exception {
        return authed(post(BASE + "/meetings/" + who.meetingId() + "/accountability"), who);
    }

    private ResultActions travel(Member who, String placeName, double minutes) throws Exception {
        return authedSend(put(BASE + "/participants/" + who.participantId() + "/travel"), who,
                travelBody(placeName, minutes));
    }

    private static String travelBody(String placeName, double minutes) {
        return "{\"placeName\":\"" + placeName + "\",\"minutes\":" + minutes + ",\"transfers\":1}";
    }

    private void registerDevice(Member who, String fcmToken) throws Exception {
        authedSend(post(BASE + "/devices"), who,
                "{\"fcmToken\":\"" + fcmToken + "\",\"platform\":\"ANDROID\",\"appVersion\":\"0.1.0\"}")
                .andExpect(status().isNoContent());
    }

    private ResultActions myAlarm(Member who) throws Exception {
        return authed(get(BASE + "/meetings/" + who.meetingId() + "/alarms"), who).andExpect(status().isOk());
    }

    private String alarmId(Member who) throws Exception {
        String json = myAlarm(who).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.data.alarm.id");
    }

    private ResultActions dismiss(Member who, String alarmId) throws Exception {
        return authedSend(post(BASE + "/alarms/" + alarmId + "/dismiss"), who,
                "{\"dismissedAt\":\"" + Instant.now() + "\",\"source\":\"DEVICE\"}");
    }

    /** 유예시간이 이미 지난 것처럼 DB와 큐를 맞춘다. */
    private void makeOverdue(String alarmId) {
        Instant past = Instant.now().minusSeconds(5);
        jdbc.update("update alarms set fire_at = ?, grace_until = ? where id = ?::uuid",
                Timestamp.from(past.minusSeconds(60)), Timestamp.from(past), alarmId);
        queue.add(UUID.fromString(alarmId), past);
    }

    private Long queueScore(String alarmId) {
        Double score = redis.opsForZSet().score(EscalationQueue.KEY, alarmId);
        return score == null ? null : score.longValue();
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
