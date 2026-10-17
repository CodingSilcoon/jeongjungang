package com.jeongjungang.alarm.api;

import com.jeongjungang.alarm.DismissSource;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** 책임 알람 요청 본문 (docs/API.md 7절). */
public final class AlarmRequests {

    private AlarmRequests() {
    }

    /** 보내지 않은 값은 지금 설정을 유지한다. */
    public record EnableAccountability(
            @Min(30) @Max(300) Integer gracePeriodSec,
            @Min(0) @Max(60) Integer marginMinutes) {

        public static final EnableAccountability DEFAULTS = new EnableAccountability(null, null);
    }

    /** 앱이 계산한 이동시간. 1~8호선 안에서 10시간을 넘는 경로는 없다. */
    public record ReportTravel(
            @NotBlank @Size(max = 60) String placeName,
            @NotNull @DecimalMin("0") @DecimalMax("600") Double minutes,
            @NotNull @Min(0) @Max(20) Integer transfers) {
    }

    /**
     * dismissedAt은 기기 기준 시각이라 기록하지 않고, 판단은 서버가 받은 시각으로 한다.
     * 오프라인 큐로 늦게 온 보고가 이미 ESCALATED된 알람을 되돌리지 않게 하기 위함이다.
     */
    public record DismissAlarm(OffsetDateTime dismissedAt, DismissSource source) {

        public static final DismissAlarm EMPTY = new DismissAlarm(null, null);

        public DismissSource sourceOrDefault() {
            return source == null ? DismissSource.DEVICE : source;
        }
    }
}
