package com.jeongjungang.alarm;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 알람시각과 에스컬레이션 기한 (docs/API.md 7절).
 * fireAt = meetAt − travelMinutes − prepMinutes − marginMinutes, graceUntil = fireAt + gracePeriodSec.
 * 앱의 AlarmTimeCalculator와 같게 이동시간은 분 단위로 올리고, 결과는 분 단위로 내린다.
 */
public record AlarmTimes(Instant fireAt, Instant graceUntil) {

    /** 0.1분 단위로 보고된 값이 부동소수 오차로 27.000001이 돼도 28분이 되지 않게 한다. */
    private static final double CEIL_EPSILON = 1e-9;

    public static AlarmTimes of(Instant meetAt, double travelMinutes, int prepMinutes, int marginMinutes,
                                int gracePeriodSec) {
        long travel = (long) Math.ceil(travelMinutes - CEIL_EPSILON);
        Instant fireAt = meetAt.minus(Duration.ofMinutes(travel + prepMinutes + marginMinutes))
                .truncatedTo(ChronoUnit.MINUTES);
        return new AlarmTimes(fireAt, fireAt.plusSeconds(gracePeriodSec));
    }
}
