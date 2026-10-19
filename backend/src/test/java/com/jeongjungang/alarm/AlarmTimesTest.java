package com.jeongjungang.alarm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 앱의 AlarmTimeCalculator와 같은 값을 내야 한다 (docs/API.md 7절). */
class AlarmTimesTest {

    private static final Instant MEET_AT = Instant.parse("2026-12-10T10:00:00Z");

    @Test
    void fireAt_subtractsTravelPrepAndMargin_graceIsAdded() {
        AlarmTimes times = AlarmTimes.of(MEET_AT, 27.0, 40, 5, 60);

        assertThat(times.fireAt()).isEqualTo("2026-12-10T08:48:00Z");
        assertThat(times.graceUntil()).isEqualTo("2026-12-10T08:49:00Z");
    }

    @Test
    void travel_isRoundedUpToWholeMinutes() {
        assertThat(AlarmTimes.of(MEET_AT, 24.2, 0, 0, 60).fireAt()).isEqualTo("2026-12-10T09:35:00Z");
    }

    @Test
    void travel_floatingPointNoiseDoesNotAddAMinute() {
        assertThat(AlarmTimes.of(MEET_AT, 27.000000000001, 0, 0, 60).fireAt()).isEqualTo("2026-12-10T09:33:00Z");
    }

    @Test
    void fireAt_isAlignedToTheMinuteEvenWhenMeetAtIsNot() {
        Instant odd = Instant.parse("2026-12-10T10:00:30Z");

        assertThat(AlarmTimes.of(odd, 10, 0, 0, 30).fireAt()).isEqualTo("2026-12-10T09:50:00Z");
    }
}
