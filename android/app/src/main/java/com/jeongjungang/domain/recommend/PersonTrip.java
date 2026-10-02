package com.jeongjungang.domain.recommend;

/** 한 사람이 후보 역까지 가는 데 걸리는 시간(접근 시간 포함)과 환승 횟수. */
public final class PersonTrip {

    public final String name;
    public final double minutes;
    public final int transfers;

    public PersonTrip(String name, double minutes, int transfers) {
        this.name = name;
        this.minutes = minutes;
        this.transfers = transfers;
    }
}
