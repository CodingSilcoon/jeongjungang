package com.jeongjungang.domain.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.jeongjungang.domain.model.LatLng;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class GeoMedianTest {

    private static final double DELTA = 1e-5;

    @Test
    public void singlePoint_returnsThatPoint() {
        LatLng result = GeoMedian.compute(Collections.singletonList(new LatLng(37.5, 127.0)));
        assertEquals(37.5, result.lat, DELTA);
        assertEquals(127.0, result.lng, DELTA);
    }

    @Test
    public void twoPoints_returnsMidpoint() {
        LatLng result = GeoMedian.compute(Arrays.asList(new LatLng(37.0, 127.0), new LatLng(38.0, 129.0)));
        assertEquals(37.5, result.lat, DELTA);
        assertEquals(128.0, result.lng, DELTA);
    }

    @Test
    public void symmetricSquare_returnsCenter() {
        LatLng result = GeoMedian.compute(Arrays.asList(
                new LatLng(0, 0), new LatLng(0, 2), new LatLng(2, 0), new LatLng(2, 2)));
        assertEquals(1.0, result.lat, DELTA);
        assertEquals(1.0, result.lng, DELTA);
    }

    @Test
    public void heavilyClusteredPoints_pullsTowardCluster() {
        LatLng result = GeoMedian.compute(Arrays.asList(
                new LatLng(0, 0), new LatLng(0, 0.001), new LatLng(0.001, 0), new LatLng(10, 10)));
        assertEquals(0.0, result.lat, 0.01);
        assertEquals(0.0, result.lng, 0.01);
    }

    @Test
    public void allPointsIdentical_returnsThatPointWithoutNaN() {
        LatLng p = new LatLng(37.5665, 126.9780);
        LatLng result = GeoMedian.compute(Arrays.asList(p, p, p));
        assertFalse(Double.isNaN(result.lat) || Double.isNaN(result.lng));
        assertEquals(37.5665, result.lat, DELTA);
        assertEquals(126.9780, result.lng, DELTA);
    }

    @Test
    public void majorityOnOnePoint_returnsThatPoint() {
        // 3명이 같은 곳이면 나머지 2명이 당겨도 그 지점이 기하 중앙값이다.
        LatLng home = new LatLng(37.5, 127.0);
        LatLng result = GeoMedian.compute(Arrays.asList(
                home, home, home, new LatLng(37.6, 127.1), new LatLng(37.4, 126.9)));
        assertEquals(37.5, result.lat, DELTA);
        assertEquals(127.0, result.lng, DELTA);
    }

    @Test
    public void collinearThreePoints_returnsMiddlePoint() {
        LatLng result = GeoMedian.compute(Arrays.asList(
                new LatLng(37.0, 127.0), new LatLng(37.0, 127.1), new LatLng(37.0, 127.4)));
        assertEquals(37.0, result.lat, DELTA);
        assertEquals(127.1, result.lng, DELTA);
    }

    @Test
    public void longitudeIsScaledByLatitude() {
        // 위도 60도에서 경도 1도는 위도 1도의 약 절반 거리다.
        // 도(degree) 그대로 계산하면 꼭짓점 각이 120도를 넘어 위쪽 점(61)이 중앙값이 되지만,
        // 실거리로 보정하면 페르마 점인 위도 약 60.57이 나와야 한다.
        LatLng result = GeoMedian.compute(Arrays.asList(
                new LatLng(60, 8), new LatLng(60, 12), new LatLng(61, 10)));
        assertEquals(60.571, result.lat, 0.01);
        assertEquals(10.0, result.lng, 1e-3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyList_throws() {
        GeoMedian.compute(Collections.<LatLng>emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void null_throws() {
        GeoMedian.compute(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonFiniteCoordinate_throws() {
        GeoMedian.compute(Arrays.asList(new LatLng(37, 127), new LatLng(Double.NaN, 127)));
    }
}
