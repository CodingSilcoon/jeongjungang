package com.jeongjungang.domain.alarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.TimeZone;
import org.junit.Test;

public class AlarmTimeCalculatorTest {

    private static final TimeZone SEOUL = TimeZone.getTimeZone("Asia/Seoul");
    private static final long MINUTE = 60_000L;

    private static long seoul(int year, int month, int day, int hour, int minute) {
        Calendar c = Calendar.getInstance(SEOUL);
        c.clear();
        c.set(year, month - 1, day, hour, minute, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void subtractsTravelPrepAndMargin() {
        long meetAt = seoul(2026, 10, 10, 19, 0);
        // 19:00 - 35분 이동 - 30분 준비 - 10분 여유 = 17:45
        assertEquals(seoul(2026, 10, 10, 17, 45), AlarmTimeCalculator.fireAtMillis(meetAt, 35, 30, 10));
    }

    @Test
    public void travelIsRoundedUpSoYouAreNotLate() {
        long meetAt = seoul(2026, 10, 10, 19, 0);
        assertEquals(meetAt - 25 * MINUTE, AlarmTimeCalculator.fireAtMillis(meetAt, 24.2, 0, 0));
        assertEquals(meetAt - 24 * MINUTE, AlarmTimeCalculator.fireAtMillis(meetAt, 24.0, 0, 0));
        // 부동소수 오차로 24.000000001이 25가 되지 않는다
        assertEquals(meetAt - 24 * MINUTE, AlarmTimeCalculator.fireAtMillis(meetAt, 24.0000000001, 0, 0));
    }

    @Test
    public void resultIsOnWholeMinute() {
        long meetAt = seoul(2026, 10, 10, 19, 0) + 30_000; // 19:00:30
        long fireAt = AlarmTimeCalculator.fireAtMillis(meetAt, 10, 0, 0);
        assertEquals(0, fireAt % MINUTE);
        assertEquals(seoul(2026, 10, 10, 18, 50), fireAt);
    }

    @Test
    public void defaultMarginMatchesApiDoc() {
        assertEquals(5, AlarmTimeCalculator.DEFAULT_MARGIN_MINUTES);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeTravelRejected() {
        AlarmTimeCalculator.fireAtMillis(0, -1, 0, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nanTravelRejected() {
        AlarmTimeCalculator.fireAtMillis(0, Double.NaN, 0, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void prepOverApiLimitRejected() {
        AlarmTimeCalculator.fireAtMillis(0, 10, AlarmTimeCalculator.MAX_PREP_MINUTES + 1, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeMarginRejected() {
        AlarmTimeCalculator.fireAtMillis(0, 10, 0, -5);
    }

    @Test
    public void lateNightIsMidnightToFive() {
        assertTrue(AlarmTimeCalculator.isLateNight(seoul(2026, 10, 10, 0, 0), SEOUL));
        assertTrue(AlarmTimeCalculator.isLateNight(seoul(2026, 10, 10, 4, 59), SEOUL));
        assertFalse(AlarmTimeCalculator.isLateNight(seoul(2026, 10, 10, 5, 0), SEOUL));
        assertFalse(AlarmTimeCalculator.isLateNight(seoul(2026, 10, 10, 23, 59), SEOUL));
    }
}
