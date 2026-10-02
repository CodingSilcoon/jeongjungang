package com.jeongjungang.domain.transit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.jeongjungang.domain.transit.TransitCsv.IntervalRow;
import com.jeongjungang.domain.transit.TransitCsv.TransferRow;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;

public class TransitGraphTest {

    private static final double DELTA = 1e-6;

    private static IntervalRow row(int line, String station, double minutes, double km) {
        return new IntervalRow(line, station, minutes, km);
    }

    private static List<IntervalRow> twoLines() {
        return Arrays.asList(
                row(1, "A", 0, 0), row(1, "B", 2, 1), row(1, "X", 2, 1),
                row(2, "X", 0, 0), row(2, "D", 3, 1.5));
    }

    private static List<TransferRow> noTransfers() {
        return Collections.<TransferRow>emptyList();
    }

    @Test
    public void adjacentRowsOnSameLineAreConnected() {
        TransitGraph g = TransitGraph.build(twoLines(), noTransfers());
        assertTrue(g.neighborStations(1, "B").contains("A"));
        assertTrue(g.neighborStations(1, "B").contains("X"));
        assertFalse(g.neighborStations(1, "A").contains("X"));
    }

    @Test
    public void lineStartRowIsNotConnectedToPreviousLineRows() {
        TransitGraph g = TransitGraph.build(twoLines(), noTransfers());
        assertEquals(new HashSet<String>(Arrays.asList("D")), g.neighborStations(2, "X"));
    }

    @Test
    public void sameNameStationsOnDifferentLinesGetDefaultTransfer() {
        TransitGraph g = TransitGraph.build(twoLines(), noTransfers());
        assertEquals(TransitGraph.DEFAULT_TRANSFER_MINUTES, g.transferMinutes(1, 2, "X"), DELTA);
        assertEquals(TransitGraph.DEFAULT_TRANSFER_MINUTES, g.transferMinutes(2, 1, "X"), DELTA);
    }

    @Test
    public void transferTableValueOverridesDefaultInEitherDirection() {
        List<TransferRow> transfers = Arrays.asList(new TransferRow(2, "X", 1, 100, 1.5));
        TransitGraph g = TransitGraph.build(twoLines(), transfers);
        assertEquals(1.5, g.transferMinutes(1, 2, "X"), DELTA);
        assertEquals(1.5, g.transferMinutes(2, 1, "X"), DELTA);
    }

    @Test
    public void transferRowsForOtherOperatorsOrUnknownStationsAreIgnored() {
        List<TransferRow> transfers = Arrays.asList(
                new TransferRow(1, "X", -1, 300, 4.3),
                new TransferRow(1, "Q", 2, 100, 1.0));
        TransitGraph g = TransitGraph.build(twoLines(), transfers);
        assertEquals(TransitGraph.DEFAULT_TRANSFER_MINUTES, g.transferMinutes(1, 2, "X"), DELTA);
    }

    @Test
    public void transferMinutes_isNullWhenStationIsNotSharedBetweenLines() {
        TransitGraph g = TransitGraph.build(twoLines(), noTransfers());
        assertNull(g.transferMinutes(1, 2, "B"));
    }

    @Test
    public void junctionTableConnectsBranchStartToJunctionNotPreviousRow() {
        List<IntervalRow> rows = Arrays.asList(
                row(2, "시청", 0, 0), row(2, "을지로입구", 1.5, 0.7), row(2, "성수", 1, 0.8),
                row(2, "시청", 1.5, 1.1),
                row(2, "용답", 3, 2.3), row(2, "신답", 1.5, 1.0));
        TransitGraph g = TransitGraph.build(rows, noTransfers());
        assertTrue(g.neighborStations(2, "용답").contains("성수"));
        assertFalse(g.neighborStations(2, "용답").contains("시청"));
        assertTrue(g.neighborStations(2, "용답").contains("신답"));
    }

    private static List<IntervalRow> line2WithShuttleBranch() {
        return Arrays.asList(
                row(2, "시청", 0, 0), row(2, "성수", 5, 3), row(2, "건대입구", 1.5, 1.2),
                row(2, "용답", 3, 2.3), row(2, "신답", 1.5, 1.0));
    }

    @Test
    public void shuttleBranchNeedsTransferAtJunctionUsingTableTime() {
        List<TransferRow> transfers = Arrays.asList(new TransferRow(2, "성수", 2, 23, 0.5));
        TransitGraph g = TransitGraph.build(line2WithShuttleBranch(), transfers);
        assertEquals(0.5, g.transferMinutes(2, 2, "성수"), DELTA);
        java.util.Map<String, Double> src = new java.util.LinkedHashMap<String, Double>();
        src.put("건대입구", 0.0);
        RouteFinder.Route toYongdap = RouteFinder.find(g, src).get("용답");
        // 건대입구 -> 성수 1.5분, 지선 환승 0.5분, 성수 -> 용답 3분
        assertEquals(1.5 + 0.5 + 3.0, toYongdap.minutes, DELTA);
        assertEquals(1, toYongdap.transfers);
    }

    @Test
    public void shuttleBranchTransferFallsBackToDefaultWhenTableHasNoRow() {
        TransitGraph g = TransitGraph.build(line2WithShuttleBranch(), noTransfers());
        assertEquals(TransitGraph.DEFAULT_TRANSFER_MINUTES, g.transferMinutes(2, 2, "성수"), DELTA);
    }

    @Test
    public void throughRunningBranchNeedsNoTransferAtJunction() {
        List<IntervalRow> rows = Arrays.asList(
                row(5, "방화", 0, 0), row(5, "강동", 5, 3), row(5, "길동", 1.5, 0.9),
                row(5, "둔촌동", 2, 1.2));
        TransitGraph g = TransitGraph.build(rows, noTransfers());
        java.util.Map<String, Double> src = new java.util.LinkedHashMap<String, Double>();
        src.put("방화", 0.0);
        RouteFinder.Route toDunchon = RouteFinder.find(g, src).get("둔촌동");
        assertEquals(5.0 + 2.0, toDunchon.minutes, DELTA);
        assertEquals(0, toDunchon.transfers);
        assertNull(g.transferMinutes(5, 5, "강동"));
    }

    @Test
    public void dwellIsAddedToEveryRideEdgeButNotToTransfers() {
        TransitGraph g = TransitGraph.build(twoLines(), noTransfers(), 0.5);
        java.util.Map<String, Double> src = new java.util.LinkedHashMap<String, Double>();
        src.put("A", 0.0);
        java.util.Map<String, RouteFinder.Route> r = RouteFinder.find(g, src);
        // A-B 2+0.5, B-X 2+0.5
        assertEquals(5.0, r.get("X").minutes, DELTA);
        // X에서 환승 4.0(정차시간 없음) + X-D 3+0.5
        assertEquals(5.0 + 4.0 + 3.5, r.get("D").minutes, DELTA);
        assertEquals(1, r.get("D").transfers);
    }

    @Test
    public void defaultDwellConstantIsHalfAMinute() {
        assertEquals(0.5, TransitGraph.DEFAULT_DWELL_MINUTES_PER_STOP, DELTA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeDwellThrows() {
        TransitGraph.build(twoLines(), noTransfers(), -0.1);
    }

    @Test
    public void repeatedStationRowClosesLoopOntoSameNode() {
        List<IntervalRow> rows = Arrays.asList(
                row(6, "응암", 0, 0), row(6, "역촌", 1.5, 1.1), row(6, "구산", 1.5, 0.9),
                row(6, "응암", 2, 1.5), row(6, "새절", 1.5, 0.9));
        TransitGraph g = TransitGraph.build(rows, noTransfers());
        assertEquals(new HashSet<String>(Arrays.asList("역촌", "구산", "새절")), g.neighborStations(6, "응암"));
    }

    @Test
    public void unknownStationHasNoNeighbors() {
        TransitGraph g = TransitGraph.build(twoLines(), noTransfers());
        assertTrue(g.neighborStations(1, "없는역").isEmpty());
        assertFalse(g.hasStation("없는역"));
        assertTrue(g.hasStation("X"));
    }
}
