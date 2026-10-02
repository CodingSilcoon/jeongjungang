package com.jeongjungang.domain.transit;

import com.jeongjungang.domain.geo.GeoDistance;
import com.jeongjungang.domain.model.LatLng;
import com.jeongjungang.domain.transit.TransitCsv.CoordRow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 역명 -> 좌표. 그래프에 있는 역만 담고, 여러 호선 좌표가 있으면 평균을 쓴다.
 * 좌표가 없는 역(예: 8호선 암사역사공원)은 검색에서 빠진다.
 */
public final class StationIndex {

    public static final class Nearby {
        public final String station;
        public final double meters;

        Nearby(String station, double meters) {
            this.station = station;
            this.meters = meters;
        }
    }

    private final Map<String, LatLng> coordinates = new LinkedHashMap<String, LatLng>();

    public StationIndex(List<CoordRow> rows, TransitGraph graph) {
        Map<String, double[]> sums = new LinkedHashMap<String, double[]>();
        for (CoordRow row : rows) {
            if (!graph.hasStation(row.station)) {
                continue;
            }
            double[] sum = sums.get(row.station);
            if (sum == null) {
                sum = new double[3];
                sums.put(row.station, sum);
            }
            sum[0] += row.lat;
            sum[1] += row.lng;
            sum[2] += 1;
        }
        for (Map.Entry<String, double[]> e : sums.entrySet()) {
            double[] s = e.getValue();
            coordinates.put(e.getKey(), new LatLng(s[0] / s[2], s[1] / s[2]));
        }
    }

    public int stationCount() {
        return coordinates.size();
    }

    /** 좌표가 없으면 null. */
    public LatLng coordinateOf(String station) {
        return coordinates.get(station);
    }

    /** 가까운 순으로 최대 k개. */
    public List<Nearby> nearest(LatLng origin, int k) {
        if (k < 1) {
            throw new IllegalArgumentException("k는 1 이상이어야 합니다: " + k);
        }
        List<Nearby> all = new ArrayList<Nearby>();
        for (Map.Entry<String, LatLng> e : coordinates.entrySet()) {
            all.add(new Nearby(e.getKey(), GeoDistance.haversineMeters(origin, e.getValue())));
        }
        Collections.sort(all, new Comparator<Nearby>() {
            @Override
            public int compare(Nearby a, Nearby b) {
                return Double.compare(a.meters, b.meters);
            }
        });
        return new ArrayList<Nearby>(all.subList(0, Math.min(k, all.size())));
    }
}
