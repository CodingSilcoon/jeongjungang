package com.jeongjungang.geocode.kakao;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/** 카카오 로컬 API 응답 중 쓰는 필드만. 좌표는 문자열이고 x가 경도, y가 위도다. */
public final class KakaoResponses {

    private KakaoResponses() {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Meta(boolean isEnd) {
    }

    /** 키워드·카테고리 검색 결과 하나. distance는 중심 좌표를 보냈을 때만 있다(미터, 문자열). */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Place(String placeName, String categoryName, String addressName, String roadAddressName,
                        String x, String y, String distance, String placeUrl) {
    }

    public record PlaceResult(Meta meta, List<Place> documents) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AddressName(String addressName) {
    }

    /**
     * 주소 검색 결과 하나. addressName은 검색어에 맞춘 전체 주소(도로명으로 찾으면 도로명),
     * address는 지번 주소, roadAddress는 도로명 주소다. 둘 다 없을 수 있다.
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Address(String addressName, String x, String y, AddressName address, AddressName roadAddress) {
    }

    public record AddressResult(List<Address> documents) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CoordAddress(AddressName address, AddressName roadAddress) {
    }

    public record CoordAddressResult(List<CoordAddress> documents) {
    }
}
