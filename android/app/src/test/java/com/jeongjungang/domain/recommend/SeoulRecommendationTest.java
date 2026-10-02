package com.jeongjungang.domain.recommend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.jeongjungang.domain.model.LatLng;
import com.jeongjungang.domain.transit.StationIndex;
import com.jeongjungang.domain.transit.TransitCsv;
import com.jeongjungang.domain.transit.TransitCsv.CoordRow;
import com.jeongjungang.domain.transit.TransitCsv.IntervalRow;
import com.jeongjungang.domain.transit.TransitCsv.TransferRow;
import com.jeongjungang.domain.transit.TransitGraph;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

/** 번들된 실제 CSV로 시안과 같은 시나리오(홍대입구, 노원, 천호)를 추천해 본다. 모듈 디렉터리에서 실행한다. */
public class SeoulRecommendationTest {

    private static final String DIR = "src/main/assets/data/";
    private static Recommender recommender;
    private static StationIndex index;

    private static Reader open(String name) throws IOException {
        return new BufferedReader(
                new InputStreamReader(Files.newInputStream(Paths.get(DIR + name)), StandardCharsets.UTF_8));
    }

    @BeforeClass
    public static void load() throws IOException {
        Reader a = open("station_intervals.csv");
        Reader b = open("station_coords.csv");
        Reader c = open("transfers.csv");
        List<IntervalRow> intervals;
        List<CoordRow> coords;
        List<TransferRow> transfers;
        try {
            intervals = TransitCsv.parseIntervals(a);
            coords = TransitCsv.parseCoords(b);
            transfers = TransitCsv.parseTransfers(c);
        } finally {
            a.close();
            b.close();
            c.close();
        }
        TransitGraph graph = TransitGraph.build(intervals, transfers, TransitGraph.DEFAULT_DWELL_MINUTES_PER_STOP);
        index = new StationIndex(coords, graph);
        recommender = new Recommender(graph, index);
    }

    private static Participant at(String name, String station) {
        LatLng p = index.coordinateOf(station);
        assertNotNull("좌표 없음: " + station, p);
        return new Participant(name, p);
    }

    private static List<Participant> hongdaeNowonCheonho() {
        return Arrays.asList(at("민수", "홍대입구"), at("지현", "노원"), at("도윤", "천호"));
    }

    @Test
    public void totalTime_returnsThreeSortedCandidatesWithConsistentMetrics() {
        List<Recommendation> r = recommender.recommend(hongdaeNowonCheonho(), Criterion.TOTAL_TIME);
        assertEquals(3, r.size());
        for (int i = 0; i < r.size(); i++) {
            Recommendation c = r.get(i);
            assertEquals(3, c.trips.size());
            double total = 0;
            double max = 0;
            int transfers = 0;
            for (PersonTrip t : c.trips) {
                total += t.minutes;
                max = Math.max(max, t.minutes);
                transfers = Math.max(transfers, t.transfers);
            }
            assertEquals(total, c.totalMinutes, 1e-6);
            assertEquals(max, c.maxMinutes, 1e-6);
            assertEquals(transfers, c.maxTransfers);
            assertTrue(c.reason.length() > 0);
            assertTrue(!c.lines.isEmpty());
            if (i > 0) {
                assertTrue(Math.round(r.get(i - 1).totalMinutes) <= Math.round(c.totalMinutes));
            }
        }
    }

    @Test
    public void maxTime_isSortedByLongestTripInWholeMinutes() {
        List<Recommendation> r = recommender.recommend(hongdaeNowonCheonho(), Criterion.MAX_TIME);
        assertEquals(3, r.size());
        for (int i = 1; i < r.size(); i++) {
            assertTrue(Math.round(r.get(i - 1).maxMinutes) <= Math.round(r.get(i).maxMinutes));
        }
    }

    @Test
    public void sameWholeMinutes_prefersFewerTransfersInRealData() {
        // 홍대입구·노원·천호에서 신설동은 최대 26.8분(환승 3회), 답십리는 26.9분(환승 1회)이다.
        List<Recommendation> r = recommender.recommend(hongdaeNowonCheonho(), Criterion.MAX_TIME);
        assertEquals("답십리", r.get(0).station);
        assertEquals(1, r.get(0).maxTransfers);
    }

    @Test
    public void bestMaxTimeNeverExceedsTheMaxOfTheBestTotalTimeWinnerBeyondRounding() {
        double maxOfTotalWinner = recommender.recommend(hongdaeNowonCheonho(), Criterion.TOTAL_TIME).get(0).maxMinutes;
        double maxOfMaxWinner = recommender.recommend(hongdaeNowonCheonho(), Criterion.MAX_TIME).get(0).maxMinutes;
        assertTrue(Math.round(maxOfMaxWinner) <= Math.round(maxOfTotalWinner));
    }

    @Test
    public void everyoneStartingAtTheSameStation_recommendsThatStationFirst() {
        List<Recommendation> r = recommender.recommend(
                Arrays.asList(at("민수", "강남"), at("지현", "강남"), at("도윤", "강남")), Criterion.TOTAL_TIME);
        assertEquals("강남", r.get(0).station);
        assertEquals(0.0, r.get(0).totalMinutes, 1e-6);
    }
}
