package com.jeongjungang.alarm;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** 알람 화면과 알림에 같이 쓰는 문구. */
public final class AlarmTexts {

    private AlarmTexts() {}

    /** 예: "19:00 답십리역 약속", 장소·시각을 모르면 "출발할 시간이에요". */
    public static String subtitle(AlarmSpec spec) {
        return subtitle(spec, TimeZone.getDefault());
    }

    static String subtitle(AlarmSpec spec, TimeZone zone) {
        StringBuilder sb = new StringBuilder();
        if (spec.meetAtMillis > 0) {
            SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.KOREA);
            f.setTimeZone(zone);
            sb.append(f.format(new Date(spec.meetAtMillis)));
        }
        if (spec.place != null && !spec.place.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(spec.place);
        }
        return sb.length() == 0 ? "출발할 시간이에요" : sb.append(" 약속").toString();
    }
}
