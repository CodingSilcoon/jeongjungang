package com.jeongjungang.domain.recommend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 추천 후보 한 곳. 목록 안의 순서가 곧 순위다. */
public final class Recommendation {

    public final String station;
    /** 이 역을 지나는 호선(오름차순). */
    public final List<Integer> lines;
    /** 입력한 참가자 순서대로. */
    public final List<PersonTrip> trips;
    public final double totalMinutes;
    public final double maxMinutes;
    public final int maxTransfers;
    public final String reason;

    Recommendation(String station, List<Integer> lines, List<PersonTrip> trips, String reason) {
        double total = 0;
        double max = 0;
        int transfers = 0;
        for (PersonTrip t : trips) {
            total += t.minutes;
            max = Math.max(max, t.minutes);
            transfers = Math.max(transfers, t.transfers);
        }
        this.station = station;
        this.lines = Collections.unmodifiableList(new ArrayList<Integer>(lines));
        this.trips = Collections.unmodifiableList(new ArrayList<PersonTrip>(trips));
        this.totalMinutes = total;
        this.maxMinutes = max;
        this.maxTransfers = transfers;
        this.reason = reason;
    }
}
