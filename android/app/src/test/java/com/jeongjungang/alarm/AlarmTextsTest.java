package com.jeongjungang.alarm;

import static org.junit.Assert.assertEquals;

import java.util.Calendar;
import java.util.TimeZone;
import org.junit.Test;

public class AlarmTextsTest {

    private static final TimeZone SEOUL = TimeZone.getTimeZone("Asia/Seoul");

    private static long seoul(int hour, int minute) {
        Calendar c = Calendar.getInstance(SEOUL);
        c.clear();
        c.set(2026, Calendar.OCTOBER, 10, hour, minute, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void timeAndPlace() {
        assertEquals("19:00 답십리역 약속",
                AlarmTexts.subtitle(new AlarmSpec("a", 1, "모임", "답십리역", seoul(19, 0)), SEOUL));
    }

    @Test
    public void onlyTimeOrOnlyPlace() {
        assertEquals("09:05 약속", AlarmTexts.subtitle(new AlarmSpec("a", 1, "모임", null, seoul(9, 5)), SEOUL));
        assertEquals("답십리역 약속", AlarmTexts.subtitle(new AlarmSpec("a", 1, "모임", "답십리역", 0), SEOUL));
    }

    @Test
    public void nothingKnown() {
        assertEquals("출발할 시간이에요", AlarmTexts.subtitle(new AlarmSpec("a", 1, "모임", "", 0), SEOUL));
    }

    @Test(expected = IllegalArgumentException.class)
    public void specNeedsId() {
        new AlarmSpec("", 1, "모임", null, 0);
    }
}
