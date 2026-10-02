package com.jeongjungang.domain.transit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.jeongjungang.domain.transit.TransitCsv.CoordRow;
import com.jeongjungang.domain.transit.TransitCsv.IntervalRow;
import com.jeongjungang.domain.transit.TransitCsv.TransferRow;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;

/** 번들된 실제 CSV로 그래프를 만들어 확인하는 통합 테스트. 모듈 디렉터리(android/app)에서 실행한다. */
public class SeoulMetroDataTest {

    private static final String DIR = "src/main/assets/data/";
    private static List<IntervalRow> intervals;
    private static List<CoordRow> coords;
    private static List<TransferRow> transfers;
    private static TransitGraph graph;

    private static Reader open(String name) throws IOException {
        return new BufferedReader(
                new InputStreamReader(Files.newInputStream(Paths.get(DIR + name)), StandardCharsets.UTF_8));
    }

    @BeforeClass
    public static void load() throws IOException {
        Reader a = open("station_intervals.csv");
        Reader b = open("station_coords.csv");
        Reader c = open("transfers.csv");
        try {
            intervals = TransitCsv.parseIntervals(a);
            coords = TransitCsv.parseCoords(b);
            transfers = TransitCsv.parseTransfers(c);
        } finally {
            a.close();
            b.close();
            c.close();
        }
        graph = TransitGraph.build(intervals, transfers, TransitGraph.DEFAULT_DWELL_MINUTES_PER_STOP);
    }

    private static RouteFinder.Route routeFrom(String origin, String destination) {
        Map<String, Double> src = new LinkedHashMap<String, Double>();
        src.put(origin, 0.0);
        return RouteFinder.find(graph, src).get(destination);
    }

    @Test
    public void rowCounts_matchSourceFiles() {
        assertEquals(279, intervals.size());
        assertEquals(276, coords.size());
        assertEquals(140, transfers.size());
    }

    @Test
    public void branchesAreAttachedToTheirJunctions() {
        assertTrue(graph.neighborStations(2, "용답").contains("성수"));
        assertTrue(graph.neighborStations(2, "용답").contains("신답"));
        assertTrue(graph.neighborStations(2, "도림천").contains("신도림"));
        assertTrue(graph.neighborStations(5, "둔촌동").contains("강동"));
        assertTrue(graph.neighborStations(5, "하남검단산").contains("하남시청"));
    }

    @Test
    public void line2LoopIsClosedAtCityHall() {
        assertTrue(graph.neighborStations(2, "시청").contains("을지로입구"));
        assertTrue(graph.neighborStations(2, "시청").contains("충정로"));
    }

    @Test
    public void line6Eungam_hasThreeNeighbors() {
        assertEquals(3, graph.neighborStations(6, "응암").size());
    }

    @Test
    public void transferTableIsApplied() {
        Double t = graph.transferMinutes(1, 4, "서울");
        assertNotNull(t);
        assertEquals(2 + 13 / 60.0, t, 1e-6);
    }

    @Test
    public void everyStationIsReachableFromCityHall() {
        Map<String, Double> src = new LinkedHashMap<String, Double>();
        src.put("시청", 0.0);
        assertEquals(graph.stationNames().size(), RouteFinder.find(graph, src).size());
    }

    private static final double DWELL = TransitGraph.DEFAULT_DWELL_MINUTES_PER_STOP;

    /** 2호선 홍대입구 -> 시청: 신촌 2:00, 이대 1:00, 아현 1:00, 충정로 1:30, 시청 1:30 (5구간, 7분),
     *  시청 -> 동대문역사문화공원: 1:30, 1:00, 1:00, 1:30 (4구간, 5분). CSV 합 12분 + 9구간 x 정차시간. */
    @Test
    public void hongdaeToDongdaemunHistoryPark_isDirectLine2() {
        RouteFinder.Route r = routeFrom("홍대입구", "동대문역사문화공원");
        assertEquals(0, r.transfers);
        assertEquals(12.0 + 9 * DWELL, r.minutes, 1e-6);
    }

    /** 4호선 노원 -> 동대문역사문화공원: CSV 11개 구간 합 18.5분 + 11구간 x 정차시간. */
    @Test
    public void nowonToDongdaemunHistoryPark_isDirectLine4() {
        RouteFinder.Route r = routeFrom("노원", "동대문역사문화공원");
        assertEquals(0, r.transfers);
        assertEquals(18.5 + 11 * DWELL, r.minutes, 1e-6);
    }

    @Test
    public void branchTransfersUseTableTimes() {
        assertEquals(19 / 60.0, graph.transferMinutes(2, 2, "성수"), 1e-6);
        assertEquals(68 / 60.0, graph.transferMinutes(2, 2, "신도림"), 1e-6);
    }

    @Test
    public void line2ShuttleBranchRequiresOneTransferAtSeongsu() {
        RouteFinder.Route r = routeFrom("건대입구", "용답");
        assertEquals(1, r.transfers);
        // 건대입구 -> 성수 1.5분, 지선 환승 19초, 성수 -> 용답 3분, 구간 2개의 정차시간
        assertEquals(1.5 + 19 / 60.0 + 3.0 + 2 * DWELL, r.minutes, 1e-6);
    }

    @Test
    public void line5MacheonBranchIsThroughRunningFromGangdong() {
        RouteFinder.Route r = routeFrom("강동", "둔촌동");
        assertEquals(0, r.transfers);
        assertEquals(110 / 60.0 + DWELL, r.minutes, 1e-6);
    }

    @Test
    public void onlyAmsaHistoryParkLacksCoordinates() {
        StationIndex idx = new StationIndex(coords, graph);
        assertNotNull(idx.coordinateOf("서울"));
        assertNotNull(idx.coordinateOf("이수"));
        assertNotNull(idx.coordinateOf("자양"));
        assertEquals(graph.stationNames().size() - 1, idx.stationCount());
        assertEquals(null, idx.coordinateOf("암사역사공원"));
    }
}
