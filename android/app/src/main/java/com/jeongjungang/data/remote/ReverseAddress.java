package com.jeongjungang.data.remote;

/** 핀 좌표를 바꾼 주소. docs/API.md `GET /geocode/reverse`. 둘 다 null일 수 있다. */
public final class ReverseAddress {

    /** 지번 주소. */
    public final String address;
    /** 도로명 주소. */
    public final String roadAddress;

    public ReverseAddress(String address, String roadAddress) {
        this.address = address;
        this.roadAddress = roadAddress;
    }

    /** 화면에 보일 한 줄. 도로명 → 지번 순으로 쓰고, 둘 다 없으면 null. */
    public String displayText() {
        if (roadAddress != null && !roadAddress.isEmpty()) {
            return roadAddress;
        }
        return address != null && !address.isEmpty() ? address : null;
    }
}
