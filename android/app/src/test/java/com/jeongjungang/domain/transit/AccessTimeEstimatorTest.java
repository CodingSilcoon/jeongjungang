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
    public void beyondWalkLimit_wholeDistanceIsByBus() {
        // 2000m 직선 -> 2600m 실거리: 버스 대기 5분 + 2600m / 250m/분 (걷는 구간 없음)
        assertEquals(5 + 2600 / 250.0, AccessTimeEstimator.estimateMinutes(2000), DELTA);
        // 3000m 직선 -> 3900m 실거리
        assertEquals(5 + 3900 / 250.0, AccessTimeEstimator.estimateMinutes(3000), DELTA);
    }

    @Test
    public void exactlyAtWalkLimit_isStillWalking() {
        // 실거리 1200m가 되는 직선거리 = 1200 / 1.3
        double straight = 1200 / 1.3;
        assertEquals(1200 / 75.0, AccessTimeEstimator.estimateMinutes(straight), 1e-6);
    }

    @Test
    public void crossingWalkLimit_switchesToBusAndGetsFaster() {
        // 직선 900m(실거리 1170m)는 걷고 15.6분, 직선 1000m(실거리 1300m)는 버스로 10.2분.
        // 이 구간에서 시간이 줄어드는 것은 "1.2km를 넘으면 전 구간 버스" 가정의 결과다.
        double walking = AccessTimeEstimator.estimateMinutes(900);
        double bus = AccessTimeEstimator.estimateMinutes(1000);
        assertEquals(1170 / 75.0, walking, DELTA);
        assertEquals(5 + 1300 / 250.0, bus, DELTA);
        assertTrue(bus < walking);
    }

    @Test
    public void withinEachMode_timeNeverDecreasesWithDistance() {
        double prev = -1;
        for (int m = 0; m <= 900; m += 50) {
            double t = AccessTimeEstimator.estimateMinutes(m);
            assertTrue("walking distance " + m, t >= prev);
            prev = t;
        }
        prev = -1;
        for (int m = 1000; m <= 8000; m += 250) {
            double t = AccessTimeEstimator.estimateMinutes(m);
            assertTrue("bus distance " + m, t >= prev);
            prev = t;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeDistance_throws() {
        AccessTimeEstimator.estimateMinutes(-1);
    }
}
