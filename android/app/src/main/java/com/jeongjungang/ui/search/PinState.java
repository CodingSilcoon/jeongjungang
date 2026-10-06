package com.jeongjungang.ui.search;

import com.jeongjungang.domain.model.LatLng;

/**
 * 지도 핀으로 고른 위치의 주소 상태. {@link AddressSearchViewModel#getPinState()}로 관찰한다.
 * 주소를 못 찾거나 실패해도 좌표는 그대로 쓸 수 있으므로 {@link #label}은 항상 채운다.
 */
public final class PinState {

    public enum Status {
        LOADING,
        /** 주소를 찾음. label이 주소다. */
        SUCCESS,
        /** 주소를 못 찾았거나 조회에 실패함. label은 대체 문구이고, 좌표로 출발지 등록은 가능하다. */
        FALLBACK
    }

    /** 주소를 모를 때 쓰는 이름. */
    public static final String DEFAULT_LABEL = "지도에서 고른 위치";

    public final Status status;
    public final LatLng location;
    /** 출발지 이름으로 쓸 한 줄. */
    public final String label;
    /** FALLBACK이 조회 실패 때문이면 그 이유. 주소가 없을 뿐이면 null. */
    public final String errorMessage;
    /**
     * 지원 지역 밖이면 true. 출발지로 등록하지 말고 {@link com.jeongjungang.domain.recommend.ServiceArea#MESSAGE}를 보여 준다.
     * LOADING 동안은 false.
     */
    public final boolean outOfArea;

    private PinState(Status status, LatLng location, String label, String errorMessage, boolean outOfArea) {
        this.status = status;
        this.location = location;
        this.label = label;
        this.errorMessage = errorMessage;
        this.outOfArea = outOfArea;
    }

    static PinState loading(LatLng location) {
        return new PinState(Status.LOADING, location, DEFAULT_LABEL, null, false);
    }

    static PinState resolved(LatLng location, String address, boolean outOfArea) {
        return address == null
                ? new PinState(Status.FALLBACK, location, DEFAULT_LABEL, null, outOfArea)
                : new PinState(Status.SUCCESS, location, address, null, outOfArea);
    }

    static PinState failed(LatLng location, String message, boolean outOfArea) {
        return new PinState(Status.FALLBACK, location, DEFAULT_LABEL, message, outOfArea);
    }
}
