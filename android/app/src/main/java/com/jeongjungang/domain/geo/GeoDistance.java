package com.jeongjungang.domain.geo;

import com.jeongjungang.domain.model.LatLng;

/** 좌표 간 거리. 순수 Java. */
public final class GeoDistance {

    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private GeoDistance() {}

    public static double haversineMeters(LatLng a, LatLng b) {
        double dLat = Math.toRadians(b.lat - a.lat);
        double dLng = Math.toRadians(b.lng - a.lng);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(h)));
    }
}
