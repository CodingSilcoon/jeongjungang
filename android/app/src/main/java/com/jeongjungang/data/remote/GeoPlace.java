package com.jeongjungang.data.remote;

import com.jeongjungang.domain.model.LatLng;

/** 주소 검색 결과 한 건. docs/API.md `GET /geocode`의 items 원소. */
public final class GeoPlace {

    public enum Type {
        /** 장소·역 이름으로 찾은 결과. */
        PLACE,
        /** 주소로 찾은 결과. */
        ADDRESS
    }

    public final Type type;
    /** 목록의 굵은 글씨. 예: "홍대입구역 2호선" */
    public final String name;
    /** 목록의 보조 글씨. 없을 수 있다(null). */
    public final String address;
    public final LatLng location;

    public GeoPlace(Type type, String name, String address, LatLng location) {
        if (type == null || name == null || location == null) {
            throw new IllegalArgumentException("종류, 이름, 좌표가 필요합니다.");
        }
        this.type = type;
        this.name = name;
        this.address = address;
        this.location = location;
    }
}
