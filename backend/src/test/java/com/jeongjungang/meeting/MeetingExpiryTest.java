package com.jeongjungang.meeting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MeetingExpiryTest {

    private static final Instant CREATED = Instant.parse("2026-10-06T00:00:00Z");

    @Test
    void withMeetAt_expires24HoursAfterIt() {
        Instant meetAt = Instant.parse("2026-10-10T10:00:00Z");
        assertThat(Meeting.expiryFor(meetAt, CREATED)).isEqualTo(Instant.parse("2026-10-11T10:00:00Z"));
    }

    @Test
    void withoutMeetAt_expires7DaysAfterCreation() {
        assertThat(Meeting.expiryFor(null, CREATED)).isEqualTo(Instant.parse("2026-10-13T00:00:00Z"));
    }

    @Test
    void changingMeetAt_movesExpiryAndBumpsVersion() {
        Meeting meeting = Meeting.create("7K3QH9MX", null, null, null, CREATED);
        long before = meeting.stateVersion();

        meeting.changeMeetAt(Instant.parse("2026-10-08T10:00:00Z"));

        assertThat(meeting.expiresAt()).isEqualTo(Instant.parse("2026-10-09T10:00:00Z"));
        assertThat(meeting.stateVersion()).isGreaterThan(before);
        assertThat(meeting.isExpired(Instant.parse("2026-10-09T10:00:00Z"))).isTrue();
        assertThat(meeting.isExpired(Instant.parse("2026-10-09T09:59:59Z"))).isFalse();
    }
}
