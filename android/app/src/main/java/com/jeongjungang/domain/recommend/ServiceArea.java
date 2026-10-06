package com.jeongjungang.domain.recommend;

import com.jeongjungang.domain.model.LatLng;
import com.jeongjungang.domain.transit.StationIndex;
import java.util.ArrayList;
import java.util.List;

/**
 * 지원 지역 판정 (2026-10-02 결정). 가장 가까운 1~8호선 역이 3km보다 멀면 지원 지역 밖이다.
 *
 * <p>우리 그래프는 서울교통공사 1~8호선뿐이라, 역에서 먼 출발지는 접근 시간을 버스 가정값으로만 채운다.
 * 서울 안에서 가장 먼 곳(우이동, 쌍문역까지 2.6km)은 통과하고, 판교(모란역까지 4.6km)부터 걸린다.
 * 이런 출발지가 섞이면 추천 전체가 틀려서 입력 단계와 추천 직전에 막는다.
 */
public final class ServiceArea {

    public static final double MAX_STATION_DISTANCE_METERS = 3000;
    public static final String MESSAGE = "지금은 서울 지하철 1~8호선 근처만 지원해요.";

    private ServiceArea() {}

    public static boolean isSupported(StationIndex index, LatLng location) {
        List<StationIndex.Nearby> nearest = index.nearest(location, 1);
        return !nearest.isEmpty() && nearest.get(0).meters <= MAX_STATION_DISTANCE_METERS;
    }

    /** 지원 지역 밖인 참가자 (입력 순서). 모두 괜찮으면 빈 목록. */
    public static List<Participant> outside(StationIndex index, List<Participant> participants) {
        List<Participant> out = new ArrayList<Participant>();
        for (Participant p : participants) {
            if (!isSupported(index, p.location)) {
                out.add(p);
            }
        }
        return out;
    }

    /** 예: "민수, 지현님 출발지는 지원 지역 밖이에요. 지금은 서울 지하철 1~8호선 근처만 지원해요." */
    public static String messageFor(List<Participant> outside) {
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < outside.size(); i++) {
            if (i > 0) {
                names.append(", ");
            }
            names.append(outside.get(i).name);
        }
        return names + "님 출발지는 지원 지역 밖이에요. " + MESSAGE;
    }
}
