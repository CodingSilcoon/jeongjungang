package com.jeongjungang.domain.alarm;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * 출발 알람 시각 계산 (CLAUDE.md "확장: 책임 알람").
 * 알람시각 = 약속시간 − 이동시간 − 준비시간 − 여유시간.
 */
public final class AlarmTimeCalculator {

    /** docs/API.md 값 제한: prepMinutes 0~240. */
    public static final int MAX_PREP_MINUTES = 240;
    /**
     * 여유시간 기본값 5분 (2026-10-02 결정, docs/API.md `marginMinutes` 기본값과 같다).
     * 이동시간이 근사값(역까지 접근은 가정값, 배차 대기 미포함)이라 실제보다 짧게 나올 수 있어서 둔다.
     * 약속마다 바꿀 수 있다.
     */
    public static final int DEFAULT_MARGIN_MINUTES = 5;
    /** 이 시각(시) 이상 ~ {@link #LATE_NIGHT_END_HOUR} 미만은 심야로 보고 경고한다. */
    public static final int LATE_NIGHT_START_HOUR = 0;
    public static final int LATE_NIGHT_END_HOUR = 5;

    private static final long MINUTE_MS = 60_000L;

    private AlarmTimeCalculator() {}

    /**
     * @param meetAtMillis   약속 시각(epoch ms)
     * @param travelMinutes  추천에서 계산한 이동시간(근사값). 분 단위로 올림한다
     * @param prepMinutes    준비시간 0~240
     * @param marginMinutes  여유시간 0 이상
     * @return 알람 시각(epoch ms, 분 단위로 맞춤)
     */
    public static long fireAtMillis(long meetAtMillis, double travelMinutes, int prepMinutes, int marginMinutes) {
        if (Double.isNaN(travelMinutes) || travelMinutes < 0) {
            throw new IllegalArgumentException("이동시간이 올바르지 않습니다: " + travelMinutes);
        }
        if (prepMinutes < 0 || prepMinutes > MAX_PREP_MINUTES) {
            throw new IllegalArgumentException("준비시간은 0~" + MAX_PREP_MINUTES + "분이어야 합니다: " + prepMinutes);
        }
        if (marginMinutes < 0) {
            throw new IllegalArgumentException("여유시간은 0 이상이어야 합니다: " + marginMinutes);
        }
        // 이동시간은 올림: 24.2분이면 25분 전에 출발해야 늦지 않는다
        long travel = (long) Math.ceil(travelMinutes - 1e-9);
        long fireAt = meetAtMillis - (travel + prepMinutes + marginMinutes) * MINUTE_MS;
        return fireAt - Math.floorMod(fireAt, MINUTE_MS);
    }

    /** 알람이 심야(0~5시)에 울리면 true. 화면에서 경고만 하고 막지는 않는다(docs/API.md 9절). */
    public static boolean isLateNight(long millis, TimeZone zone) {
        Calendar c = Calendar.getInstance(zone);
        c.setTimeInMillis(millis);
        int hour = c.get(Calendar.HOUR_OF_DAY);
        return hour >= LATE_NIGHT_START_HOUR && hour < LATE_NIGHT_END_HOUR;
    }
}
