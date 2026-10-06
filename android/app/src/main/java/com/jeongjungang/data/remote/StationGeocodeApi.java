package com.jeongjungang.data.remote;

import com.jeongjungang.data.repository.TransitData;
import com.jeongjungang.domain.model.LatLng;
import com.jeongjungang.domain.transit.StationIndex;
import com.jeongjungang.domain.transit.StationNames;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 서버 없이 쓰는 대체 검색. 번들된 1~8호선 역 이름만 찾는다("홍대" → 홍대입구역).
 * 서버 주소가 설정되지 않았을 때(개발 중, 시연 대비) 쓴다. 일반 주소는 찾지 못한다.
 */
public final class StationGeocodeApi implements GeocodeApi {

    /** 지하철 데이터를 꺼내 오는 곳. 앱에서는 TransitRepository::get. */
    public interface DataProvider {
        TransitData get() throws IOException;
    }

    private final DataProvider provider;

    public StationGeocodeApi(DataProvider provider) {
        this.provider = provider;
    }

    @Override
    public List<GeoPlace> search(String query, int size) throws ApiException {
        String q = query == null ? "" : query.trim();
        if (q.length() < MIN_QUERY_LENGTH || q.length() > MAX_QUERY_LENGTH) {
            throw new ApiException("VALIDATION_FAILED", 0,
                    "검색어는 " + MIN_QUERY_LENGTH + "~" + MAX_QUERY_LENGTH + "자로 입력해 주세요.", 0, null);
        }
        final String key = compact(StationNames.normalize(q));
        TransitData data = load();
        List<String> matches = new ArrayList<String>();
        for (String station : data.graph.stationNames()) {
            if (data.index.coordinateOf(station) != null && compact(station).contains(key)) {
                matches.add(station);
            }
        }
        // 정확히 같은 이름 → 앞부분 일치 → 짧은 이름 → 가나다 순
        Collections.sort(matches, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                int byRank = Integer.compare(rank(a, key), rank(b, key));
                if (byRank != 0) {
                    return byRank;
                }
                int byLength = Integer.compare(a.length(), b.length());
                return byLength != 0 ? byLength : a.compareTo(b);
            }
        });
        int limit = Math.max(1, Math.min(MAX_SIZE, size));
        List<GeoPlace> places = new ArrayList<GeoPlace>();
        for (String station : matches.subList(0, Math.min(limit, matches.size()))) {
            places.add(new GeoPlace(GeoPlace.Type.PLACE, station + "역",
                    linesLabel(data.graph.linesOf(station)), data.index.coordinateOf(station)));
        }
        return Collections.unmodifiableList(places);
    }

    /** 가장 가까운 역 기준으로 "홍대입구역 근처 (약 300m)"처럼 알려 준다. */
    @Override
    public ReverseAddress reverse(LatLng location) throws ApiException {
        List<StationIndex.Nearby> nearest = load().index.nearest(location, 1);
        if (nearest.isEmpty()) {
            return new ReverseAddress(null, null);
        }
        StationIndex.Nearby near = nearest.get(0);
        return new ReverseAddress(near.station + "역 근처 (약 " + distanceLabel(near.meters) + ")", null);
    }

    private TransitData load() throws ApiException {
        try {
            return provider.get();
        } catch (IOException e) {
            throw new ApiException(ApiException.BAD_RESPONSE, 0, "역 정보를 읽지 못했어요. 앱을 다시 실행해 주세요.", 0, e);
        }
    }

    private static int rank(String station, String key) {
        String s = compact(station);
        if (s.equals(key)) {
            return 0;
        }
        return s.startsWith(key) ? 1 : 2;
    }

    private static String compact(String s) {
        return s.replace(" ", "").toLowerCase(Locale.ROOT);
    }

    static String linesLabel(List<Integer> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append('·');
            }
            sb.append(lines.get(i));
        }
        return sb.length() == 0 ? null : sb.append("호선").toString();
    }

    static String distanceLabel(double meters) {
        if (meters < 1000) {
            return Math.max(10, Math.round(meters / 10.0) * 10) + "m";
        }
        return String.format(Locale.US, "%.1fkm", meters / 1000.0);
    }
}
