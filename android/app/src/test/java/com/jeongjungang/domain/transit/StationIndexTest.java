package com.jeongjungang.domain.transit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.jeongjungang.domain.model.LatLng;
import com.jeongjungang.domain.transit.TransitCsv.CoordRow;
import com.jeongjungang.domain.transit.TransitCsv.IntervalRow;
import com.jeongjungang.domain.transit.TransitCsv.TransferRow;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class StationIndexTest {

    private static TransitGraph graph() {
        return TransitGraph.build(Arrays.asList(
                new IntervalRow(1, "서울", 0, 0), new IntervalRow(1, "시청", 2, 1.1),
                new IntervalRow(1, "종각", 2, 1.0), new IntervalRow(1, "무좌표", 2, 1.0)),
                Collections.<TransferRow>emptyList());
    }

    private static List<CoordRow> coords() {
        return Arrays.asList(
                new CoordRow(1, "서울", 37.55315, 126.972533),
                new CoordRow(1, "시청", 37.56359, 126.975407),
                new CoordRow(1, "종각", 37.570203, 126.983116),
                new CoordRow(4, "서울", 37.55300, 126.972600),
                new CoordRow(1, "그래프에없음", 37.5, 127.0));
    }

    @Test
    public void nearest_returnsClosestFirst() {
        StationIndex idx = new StationIndex(coords(), graph());
        List<StationIndex.Nearby> r = idx.nearest(new LatLng(37.5640, 126.9760), 2);
        assertEquals(2, r.size());
        assertEquals("시청", r.get(0).station);
        assertTrue(r.get(0).meters < r.get(1).meters);
    }

    @Test
    public void stationsWithoutCoordsOrOutsideGraphAreExcluded() {
        StationIndex idx = new StationIndex(coords(), graph());
        List<StationIndex.Nearby> r = idx.nearest(new LatLng(37.5, 127.0), 10);
        assertEquals(3, r.size());
        for (StationIndex.Nearby n : r) {
            assertTrue(!n.station.equals("무좌표") && !n.station.equals("그래프에없음"));
        }
    }

    @Test
    public void kLargerThanStationCount_returnsAll() {
        StationIndex idx = new StationIndex(coords(), graph());
        assertEquals(3, idx.nearest(new LatLng(37.56, 126.97), 99).size());
    }

    @Test
    public void multiLineStationUsesAveragedCoordinate() {
        StationIndex idx = new StationIndex(coords(), graph());
        LatLng c = idx.coordinateOf("서울");
        assertEquals((37.55315 + 37.55300) / 2, c.lat, 1e-9);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonPositiveKThrows() {
        new StationIndex(coords(), graph()).nearest(new LatLng(37.5, 127.0), 0);
    }
}
