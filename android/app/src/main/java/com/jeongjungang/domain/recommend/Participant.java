package com.jeongjungang.domain.recommend;

import com.jeongjungang.domain.model.LatLng;

/** 약속 참가자: 표시 이름과 출발지 좌표. */
public final class Participant {

    public final String name;
    public final LatLng location;

    public Participant(String name, LatLng location) {
        if (name == null || location == null) {
            throw new IllegalArgumentException("이름과 출발지가 필요합니다.");
        }
        this.name = name;
        this.location = location;
    }
}
