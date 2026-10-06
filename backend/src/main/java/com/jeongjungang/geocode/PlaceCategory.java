package com.jeongjungang.geocode;

/**
 * 약속 목적별 주변 장소 종류. 카카오에 술집 카테고리 코드가 없어서 BAR는 키워드 "술집"으로 찾는다.
 */
public enum PlaceCategory {
    FOOD("FD6", null),
    CAFE("CE7", null),
    BAR(null, "술집");

    private final String kakaoGroupCode;
    private final String keyword;

    PlaceCategory(String kakaoGroupCode, String keyword) {
        this.kakaoGroupCode = kakaoGroupCode;
        this.keyword = keyword;
    }

    String kakaoGroupCode() {
        return kakaoGroupCode;
    }

    String keyword() {
        return keyword;
    }
}
