package com.jeongjungang.domain.geo;

import static org.junit.Assert.assertEquals;

import com.jeongjungang.domain.model.LatLng;
import org.junit.Test;

public class GeoDistanceTest {

    @Test
    public void samePoint_isZero() {
        LatLng p = new LatLng(37.5, 127.0);
        assertEquals(0.0, GeoDistance.haversineMeters(p, p), 1e-6);
    }

    @Test
    public void oneDegreeOfLatitude_isAbout111km() {
        double d = GeoDistance.haversineMeters(new LatLng(37, 127), new LatLng(38, 127));
        assertEquals(111_195, d, 200);
    }

    @Test
    public void seoulStationToCityHall_isAboutOneKilometre() {
        // 서울역(37.55315, 126.972533) - 시청(37.56359, 126.975407)
        double d = GeoDistance.haversineMeters(new LatLng(37.55315, 126.972533), new LatLng(37.56359, 126.975407));
        assertEquals(1_180, d, 60);
    }
}
