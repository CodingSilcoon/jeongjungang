package com.jeongjungang.meeting;

import static org.assertj.core.api.Assertions.assertThat;

import com.jeongjungang.IntegrationTestSupport;
import com.jeongjungang.meeting.api.MeetingRequests.CreateMeeting;
import com.jeongjungang.meeting.api.MeetingRequests.JoinMeeting;
import com.jeongjungang.meeting.api.MeetingResponses.Created;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 기한이 지난 약속과 그 참가자를 지우고, 아직 기한 안인 약속은 남긴다 (docs/API.md 4절). */
class MeetingRetentionIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MeetingService meetingService;

    @Autowired
    private MeetingRetention retention;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void purgeExpired_deletesExpiredMeetingsWithTheirParticipants() {
        Created expired = meetingService.create(new CreateMeeting(null, "지난약속", null, null, null));
        meetingService.join(expired.meeting().inviteCode(), new JoinMeeting("멤버", null));
        Created alive = meetingService.create(new CreateMeeting(null, "남은약속", null, null, null));
        jdbc.update("update meetings set expires_at = ? where id = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)), expired.meeting().id());

        int deleted = retention.purgeExpired();

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(countMeetings(expired.meeting().id())).isZero();
        assertThat(countParticipants(expired.meeting().id())).isZero();
        assertThat(countMeetings(alive.meeting().id())).isOne();
        assertThat(countParticipants(alive.meeting().id())).isOne();
    }

    private int countMeetings(UUID id) {
        return jdbc.queryForObject("select count(*) from meetings where id = ?", Integer.class, id);
    }

    private int countParticipants(UUID meetingId) {
        return jdbc.queryForObject("select count(*) from participants where meeting_id = ?", Integer.class, meetingId);
    }
}
