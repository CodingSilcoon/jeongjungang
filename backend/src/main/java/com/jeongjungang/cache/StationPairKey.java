package com.jeongjungang.cache;

/**
 * 역-역 소요시간 캐시 키. 출발/도착 순서가 다른 (A,B)와 (B,A)는 별개 키.
 * (대중교통 소요시간은 방향에 따라 다를 수 있음)
 */
public record StationPairKey(String fromStationId, String toStationId) {

    private static final String PREFIX = "travel:";

    public StationPairKey {
        if (fromStationId == null || fromStationId.isBlank()
                || toStationId == null || toStationId.isBlank()) {
            throw new IllegalArgumentException("역 ID는 비어 있을 수 없습니다.");
        }
    }

    public String toRedisKey() {
        return PREFIX + fromStationId + ":" + toStationId;
    }
}
