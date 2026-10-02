package com.jeongjungang.domain.recommend;

import com.jeongjungang.domain.geo.GeoMedian;
import com.jeongjungang.domain.model.LatLng;
import com.jeongjungang.domain.transit.AccessTimeEstimator;
import com.jeongjungang.domain.transit.RouteFinder;
import com.jeongjungang.domain.transit.StationIndex;
import com.jeongjungang.domain.transit.TransitGraph;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 참가자 출발지에서 후보 3곳을 고른다.
 * 기하 중앙값 주변 역을 후보 풀로 잡고, 사람마다 가까운 역 몇 곳에서 출발하는 최단시간을 구해
 * 전원이 갈 수 있는 후보만 기준에 맞게 정렬한다.
 */
public final class Recommender {

    /** 중앙값 주변에서 후보로 삼을 역 수. 시연하며 조정할 값이다. */
    public static final int DEFAULT_POOL_SIZE = 15;
    /** 사람마다 출발점으로 쓸 가까운 역 수. */
    public static final int DEFAULT_ACCESS_STATIONS = 3;
    public static final int RESULT_COUNT = 3;
    /**
     * 기준 시간이 이 안(분)으로 차이 나는 후보끼리는 같은 묶음으로 보고 환승이 적은 쪽을 앞세운다.
     * 1~2분 빠른데 환승이 더 많은 곳보다 덜 갈아타는 곳이 납득하기 쉬워서다 (2026-10-02 결정).
     */
    public static final int TIE_BAND_MINUTES = 2;

    private final TransitGraph graph;
    private final StationIndex index;
    private final int poolSize;
    private final int accessStations;

    public Recommender(TransitGraph graph, StationIndex index) {
        this(graph, index, DEFAULT_POOL_SIZE, DEFAULT_ACCESS_STATIONS);
    }

    public Recommender(TransitGraph graph, StationIndex index, int poolSize, int accessStations) {
        if (poolSize < 1 || accessStations < 1) {
            throw new IllegalArgumentException("후보 풀과 접근 역 수는 1 이상이어야 합니다.");
        }
        this.graph = graph;
        this.index = index;
        this.poolSize = poolSize;
        this.accessStations = accessStations;
    }

    /**
     * @return 기준에 맞는 순서의 후보(최대 3곳). 전원이 함께 갈 수 있는 후보가 없으면 빈 목록
     * @throws IllegalArgumentException 참가자가 없거나 기준이 null인 경우
     */
    public List<Recommendation> recommend(List<Participant> participants, Criterion criterion) {
        if (participants == null || participants.isEmpty() || criterion == null) {
            throw new IllegalArgumentException("참가자와 기준이 필요합니다.");
        }
        List<Map<String, RouteFinder.Route>> routesByPerson = routesForEveryone(participants);
        List<Recommendation> candidates = new ArrayList<Recommendation>();
        for (StationIndex.Nearby pooled : index.nearest(medianOf(participants), poolSize)) {
            Recommendation candidate = candidateAt(pooled.station, participants, routesByPerson);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        List<Recommendation> ranked = rank(candidates, criterion);
        return new ArrayList<Recommendation>(ranked.subList(0, Math.min(RESULT_COUNT, ranked.size())));
    }

    private static LatLng medianOf(List<Participant> participants) {
        List<LatLng> points = new ArrayList<LatLng>();
        for (Participant p : participants) {
            points.add(p.location);
        }
        return GeoMedian.compute(points);
    }

    private List<Map<String, RouteFinder.Route>> routesForEveryone(List<Participant> participants) {
        List<Map<String, RouteFinder.Route>> all = new ArrayList<Map<String, RouteFinder.Route>>();
        for (Participant p : participants) {
            Map<String, Double> access = new LinkedHashMap<String, Double>();
            for (StationIndex.Nearby near : index.nearest(p.location, accessStations)) {
                access.put(near.station, AccessTimeEstimator.estimateMinutes(near.meters));
            }
            all.add(RouteFinder.find(graph, access));
        }
        return all;
    }

    /** 한 명이라도 갈 수 없는 역이면 null. */
    private Recommendation candidateAt(String station, List<Participant> participants,
                                       List<Map<String, RouteFinder.Route>> routesByPerson) {
        List<PersonTrip> trips = new ArrayList<PersonTrip>();
        for (int i = 0; i < participants.size(); i++) {
            RouteFinder.Route route = routesByPerson.get(i).get(station);
            if (route == null) {
                return null;
            }
            trips.add(new PersonTrip(participants.get(i).name, route.minutes, route.transfers));
        }
        return new Recommendation(station, graph.linesOf(station), trips, ReasonWriter.write(trips));
    }

    /**
     * 최종 순서. {@link #comparatorFor}로 정렬한 뒤, 맨 앞 후보(묶음의 기준)보다 기준 시간이
     * {@link #TIE_BAND_MINUTES}분 이내로 더 걸리는 후보까지 한 묶음으로 보고 그 안을 환승 적은 순으로 다시 세운다.
     * 묶음 밖의 첫 후보가 다음 묶음의 기준이 된다. 기준은 묶음의 가장 빠른 후보라 이웃끼리 줄줄이 이어지지 않는다.
     * 예: 27분(환승 1) · 27분(환승 3) · 28분(환승 2) → 한 묶음 → 환승 1 · 2 · 3 순.
     */
    static List<Recommendation> rank(List<Recommendation> candidates, Criterion criterion) {
        List<Recommendation> rest = new ArrayList<Recommendation>(candidates);
        Collections.sort(rest, comparatorFor(criterion));
        List<Recommendation> out = new ArrayList<Recommendation>(rest.size());
        int start = 0;
        while (start < rest.size()) {
            long leader = primaryMinutes(rest.get(start), criterion);
            int end = start;
            while (end < rest.size() && primaryMinutes(rest.get(end), criterion) - leader <= TIE_BAND_MINUTES) {
                end++;
            }
            List<Recommendation> group = new ArrayList<Recommendation>(rest.subList(start, end));
            final Comparator<Recommendation> base = comparatorFor(criterion);
            Collections.sort(group, new Comparator<Recommendation>() {
                @Override
                public int compare(Recommendation a, Recommendation b) {
                    int byTransfers = Integer.compare(a.maxTransfers, b.maxTransfers);
                    return byTransfers != 0 ? byTransfers : base.compare(a, b);
                }
            });
            out.addAll(group);
            start = end;
        }
        return out;
    }

    /** 화면에 보이는 분 단위 기준 시간. */
    static long primaryMinutes(Recommendation r, Criterion criterion) {
        return Math.round(criterion == Criterion.TOTAL_TIME ? r.totalMinutes : r.maxMinutes);
    }

    /**
     * 묶기 전 기본 정렬: 기준 지표(화면에 보이는 분 단위) → 환승이 적은 쪽 → 보조 지표(분 단위) → 역 이름.
     * 분 단위로 같은 후보끼리는 환승이 적은 쪽을 앞세워, 0.1분 차이로 환승이 많은 후보가 1위가 되는 일을 막는다.
     */
    static Comparator<Recommendation> comparatorFor(final Criterion criterion) {
        return new Comparator<Recommendation>() {
            @Override
            public int compare(Recommendation a, Recommendation b) {
                boolean byTotal = criterion == Criterion.TOTAL_TIME;
                int byPrimary = Long.compare(
                        Math.round(byTotal ? a.totalMinutes : a.maxMinutes),
                        Math.round(byTotal ? b.totalMinutes : b.maxMinutes));
                if (byPrimary != 0) {
                    return byPrimary;
                }
                int byTransfers = Integer.compare(a.maxTransfers, b.maxTransfers);
                if (byTransfers != 0) {
                    return byTransfers;
                }
                int bySecondary = Long.compare(
                        Math.round(byTotal ? a.maxMinutes : a.totalMinutes),
                        Math.round(byTotal ? b.maxMinutes : b.totalMinutes));
                return bySecondary != 0 ? bySecondary : a.station.compareTo(b.station);
            }
        };
    }
}
