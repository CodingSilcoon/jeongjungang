package com.jeongjungang.domain.transit;

/**
 * 출발지에서 가까운 역까지의 접근시간 근사.
 * 가정값은 모두 튜닝 대상이다 (CLAUDE.md "추천 로직" 3번).
 */
public final class AccessTimeEstimator {

    /** 도보 4.5km/h */
    static final double WALK_METERS_PER_MINUTE = 4500.0 / 60;
    /** 직선거리 -> 실거리 보정 */
    static final double DETOUR_FACTOR = 1.3;
    /** 이 거리까지는 걸어서, 초과분은 버스로 간다고 가정 */
    static final double WALK_LIMIT_METERS = 1200;
    static final double BUS_WAIT_MINUTES = 5;
    /** 버스 15km/h */
    static final double BUS_METERS_PER_MINUTE = 15000.0 / 60;

    private AccessTimeEstimator() {}

    public static double estimateMinutes(double straightLineMeters) {
        if (straightLineMeters < 0 || Double.isNaN(straightLineMeters)) {
            throw new IllegalArgumentException("거리는 0 이상이어야 합니다: " + straightLineMeters);
        }
        double distance = straightLineMeters * DETOUR_FACTOR;
        if (distance <= WALK_LIMIT_METERS) {
            return distance / WALK_METERS_PER_MINUTE;
        }
        double walk = WALK_LIMIT_METERS / WALK_METERS_PER_MINUTE;
        double bus = BUS_WAIT_MINUTES + (distance - WALK_LIMIT_METERS) / BUS_METERS_PER_MINUTE;
        return walk + bus;
    }
}
