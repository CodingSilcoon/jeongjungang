package com.jeongjungang.geocode;

import java.util.Optional;

/** 카카오가 문자열로 주는 좌표를 읽는다. 잘못된 값이 섞인 결과는 버린다. */
record Coordinates(double lat, double lng) {

    static Optional<Coordinates> parse(String lat, String lng) {
        if (lat == null || lng == null) {
            return Optional.empty();
        }
        try {
            double la = Double.parseDouble(lat);
            double ln = Double.parseDouble(lng);
            if (Math.abs(la) > 90 || Math.abs(ln) > 180) {
                return Optional.empty();
            }
            return Optional.of(new Coordinates(la, ln));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
