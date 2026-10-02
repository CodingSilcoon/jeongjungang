package com.jeongjungang.domain.transit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.jeongjungang.domain.transit.TransitCsv.IntervalRow;
import com.jeongjungang.domain.transit.TransitCsv.TransferRow;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class RouteFinderTest {

    private static final double DELTA = 1e-6;

    private static IntervalRow row(int line, String station, double minutes, double km) {
        return new IntervalRow(line, station, minutes, km);
    }

    /** 1호선 A-B-C(구간 2분), 2호선 C-D(3분). C에서 환승(기본 4분). */
    private static TransitGraph graph() {
        List<IntervalRow> rows = Arrays.asList(
                row(1, "A", 0, 0), row(1, "B", 2, 1), row(1, "C", 2, 1),
                row(2, "C", 0, 0), row(2, "D", 3, 1.5));
        return TransitGraph.build(rows, Collections.<TransferRow>emptyList());
    }

    private static Map<String, Double> sources(Object... kv) {
        Map<String, Double> m = new LinkedHashMap<String, Double>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], ((Number) kv[i + 1]).doubleValue());
        }
        return m;
    }

    @Test
    public void singleSource_sameLine_hasNoTransfer() {
        Map<String, RouteFinder.Route> r = RouteFinder.find(graph(), sources("A", 0));
        assertEquals(4.0, r.get("C").minutes, DELTA);
        assertEquals(0, r.get("C").transfers);
    }

    @Test
    public void transferAddsTransferTimeAndCount() {
        Map<String, RouteFinder.Route> r = RouteFinder.find(graph(), sources("A", 0));
        assertEquals(4 + 4 + 3, r.get("D").minutes, DELTA);
        assertEquals(1, r.get("D").transfers);
    }

    @Test
    public void accessTimeIsAddedToStartingStation() {
        Map<String, RouteFinder.Route> r = RouteFinder.find(graph(), sources("A", 6.5));
        assertEquals(6.5, r.get("A").minutes, DELTA);
        assertEquals(6.5 + 2, r.get("B").minutes, DELTA);
    }

    @Test
    public void multipleSources_pickCheaperEntryPoint() {
        Map<String, RouteFinder.Route> r = RouteFinder.find(graph(), sources("A", 10, "C", 1));
        assertEquals(3.0, r.get("B").minutes, DELTA);
        assertEquals(0, r.get("B").transfers);
    }

    @Test
    public void boardingAtSourceStationOnAnyLineIsFree() {
        Map<String, RouteFinder.Route> r = RouteFinder.find(graph(), sources("C", 0));
        assertEquals(3.0, r.get("D").minutes, DELTA);
        assertEquals(0, r.get("D").transfers);
    }

    @Test
    public void equalTimePrefersFewerTransfers() {
        // 1호선 P-Q 6분, 또는 2호선 P-R(1분) -> 환승(1분) -> 3호선 R-Q(4분) = 6분
        List<IntervalRow> rows = Arrays.asList(
                row(1, "P", 0, 0), row(1, "Q", 6, 3),
                row(2, "P", 0, 0), row(2, "R", 1, 0.5),
                row(3, "R", 0, 0), row(3, "Q", 4, 2));
        List<TransferRow> transfers = Arrays.asList(new TransferRow(2, "R", 3, 50, 1.0));
        Map<String, RouteFinder.Route> r = RouteFinder.find(TransitGraph.build(rows, transfers), sources("P", 0));
        assertEquals(6.0, r.get("Q").minutes, DELTA);
        assertEquals(0, r.get("Q").transfers);
    }

    @Test
    public void unreachableStationsAreAbsent() {
        List<IntervalRow> rows = Arrays.asList(
                row(1, "A", 0, 0), row(1, "B", 2, 1),
                row(3, "Z", 0, 0), row(3, "Y", 2, 1));
        Map<String, RouteFinder.Route> r = RouteFinder.find(
                TransitGraph.build(rows, Collections.<TransferRow>emptyList()), sources("A", 0));
        assertTrue(r.containsKey("B"));
        assertFalse(r.containsKey("Y"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownSourceStationThrows() {
        RouteFinder.find(graph(), sources("없는역", 0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptySourcesThrows() {
        RouteFinder.find(graph(), new LinkedHashMap<String, Double>());
    }
}
