package com.jeongjungang.domain.transit;

/**
 * 출발지에서 가까운 역까지의 접근시간 근사.
 * 가정값은 모두 튜닝 대상이다 (CLAUDE.md "추천 로직" 3번).
 *
 * <p>실거리가 {@link #WALK_LIMIT_METERS} 이하면 걸어서, 넘으면 전 구간을 버스로 간다고 본다.
 * 그래서 경계(실거리 1.2km, 직선 약 920m)를 넘는 순간 걷는 것보다 버스가 빨라져
 * 시간이 오히려 줄어드는 구간이 있다. 이 경계 효과가 거슬리면 "도보와 버스 중 빠른 쪽" 방식으로 바꾼다.
 */
public final class AccessTimeEstimator {

    /** 도보 4.5km/h */
    static final double WALK_METERS_PER_MINUTE = 4500.0 / 60;
    /** 직선거리 -> 실거리 보정 */
    static final double DETOUR_FACTOR = 1.3;
    /** 실거리가 이 값 이하면 도보, 넘으면 전 구간 버스 */
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
        return BUS_WAIT_MINUTES + distance / BUS_METERS_PER_MINUTE;
    }
}
