package com.jeongjungang.domain.transit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AccessTimeEstimatorTest {

    private static final double DELTA = 1e-6;

    @Test
    public void zeroDistance_isZeroMinutes() {
        assertEquals(0.0, AccessTimeEstimator.estimateMinutes(0), DELTA);
    }

    @Test
    public void shortDistance_isWalkingWithDetourFactor() {
        // 500m 직선 -> 650m 실거리 -> 4.5km/h(75m/분)로 약 8.67분
        assertEquals(650 / 75.0, AccessTimeEstimator.estimateMinutes(500), DELTA);
    }

    @Test
    public void longDistance_addsBusWaitAndBusSpeedForExcess() {
        // 2000m 직선 -> 2600m 실거리: 1200m 도보(16분) + 버스 대기 5분 + 1400m / 250m/분
        double expected = 1200 / 75.0 + 5 + 1400 / 250.0;
        assertEquals(expected, AccessTimeEstimator.estimateMinutes(2000), DELTA);
    }

    @Test
    public void estimate_neverDecreasesWithDistance() {
        double prev = -1;
        for (int m = 0; m <= 5000; m += 100) {
            double t = AccessTimeEstimator.estimateMinutes(m);
            assertTrue("distance " + m, t >= prev);
            prev = t;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeDistance_throws() {
        AccessTimeEstimator.estimateMinutes(-1);
    }
}
