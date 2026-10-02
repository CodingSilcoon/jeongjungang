package com.jeongjungang.domain.recommend;

import java.util.List;

/** 후보 카드에 붙는 선정 이유 문구. 시간과 환승 숫자만으로 만든다. */
final class ReasonWriter {

    /** 한 사람만 이 시간(분) 이상 더 걸리면 그 사람을 짚어서 알려 준다. */
    static final double OUTLIER_GAP_MINUTES = 10;
    /** "전원 N분 이내"에서 N을 올림하는 단위(분). */
    static final double BUCKET_MINUTES = 10;
    /** 이보다 오래 걸리면 구간 표현 대신 가장 오래 걸리는 사람을 알려 준다. */
    static final double MAX_BUCKET_MINUTES = 90;
    private static final double EPSILON = 1e-9;
    private static final int MIN_PEOPLE_FOR_OUTLIER = 3;

    private ReasonWriter() {}

    static String write(List<PersonTrip> trips) {
        if (trips == null || trips.isEmpty()) {
            throw new IllegalArgumentException("이동 정보가 필요합니다.");
        }
        PersonTrip slowest = trips.get(0);
        int maxTransfers = 0;
        for (PersonTrip t : trips) {
            if (t.minutes > slowest.minutes) {
                slowest = t;
            }
            maxTransfers = Math.max(maxTransfers, t.transfers);
        }
        if (trips.size() >= MIN_PEOPLE_FOR_OUTLIER
                && slowest.minutes - secondSlowestMinutes(trips, slowest) >= OUTLIER_GAP_MINUTES) {
            return slowest.name + "님만 " + Math.round(slowest.minutes) + "분으로 길어요";
        }
        return timeClause(slowest) + ", " + transferClause(maxTransfers);
    }

    private static double secondSlowestMinutes(List<PersonTrip> trips, PersonTrip slowest) {
        double second = 0;
        boolean skipped = false;
        for (PersonTrip t : trips) {
            if (t == slowest && !skipped) {
                skipped = true;
                continue;
            }
            second = Math.max(second, t.minutes);
        }
        return second;
    }

    private static String timeClause(PersonTrip slowest) {
        if (slowest.minutes > MAX_BUCKET_MINUTES) {
            return "가장 오래 걸리는 " + slowest.name + "님 " + Math.round(slowest.minutes) + "분";
        }
        long bucket = (long) (Math.ceil((slowest.minutes - EPSILON) / BUCKET_MINUTES) * BUCKET_MINUTES);
        return "전원 " + bucket + "분 이내";
    }

    private static String transferClause(int maxTransfers) {
        return maxTransfers == 0 ? "환승 없음" : "환승 최대 " + maxTransfers + "회";
    }
}
