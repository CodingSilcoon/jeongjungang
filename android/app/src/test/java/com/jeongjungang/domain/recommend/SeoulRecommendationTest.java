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
        }
        assertNoLaterCandidateIsMuchFaster(r, Criterion.TOTAL_TIME);
    }

    /** 2분 이내 동률 때문에 뒤 후보가 앞보다 빠를 수는 있지만, 2분을 넘게 빠를 수는 없다. */
    private static void assertNoLaterCandidateIsMuchFaster(List<Recommendation> r, Criterion criterion) {
        for (int i = 0; i < r.size(); i++) {
            for (int j = i + 1; j < r.size(); j++) {
                assertTrue(r.get(i).station + " 뒤의 " + r.get(j).station,
                        Recommender.primaryMinutes(r.get(j), criterion)
                                >= Recommender.primaryMinutes(r.get(i), criterion) - Recommender.TIE_BAND_MINUTES);
            }
        }
    }

    @Test
    public void maxTime_isSortedByLongestTripWithTwoMinuteTieBand() {
        List<Recommendation> r = recommender.recommend(hongdaeNowonCheonho(), Criterion.MAX_TIME);
        assertEquals(3, r.size());
        assertNoLaterCandidateIsMuchFaster(r, Criterion.MAX_TIME);
    }

    @Test
    public void withinTwoMinutes_fewerTransfersComeFirstInRealData() {
        // 27~29분 묶음: 답십리 27(환승 1), 신설동 27(환승 3), 보문 28(환승 2), 마장 29(환승 1), 안암 29(환승 2), 제기동 29(환승 4)
        // → 환승 적은 순으로 답십리 · 마장 · 보문. 바꾸기 전에는 신설동(환승 3)이 2위였다.
        List<Recommendation> r = recommender.recommend(hongdaeNowonCheonho(), Criterion.MAX_TIME);
        assertEquals(Arrays.asList("답십리", "마장", "보문"),
                Arrays.asList(r.get(0).station, r.get(1).station, r.get(2).station));
        assertEquals(Arrays.asList(1, 1, 2),
                Arrays.asList(r.get(0).maxTransfers, r.get(1).maxTransfers, r.get(2).maxTransfers));
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
        // 2분 이내 동률로 환승이 적은 쪽이 1위가 될 수 있어서 그만큼은 허용한다
        assertTrue(Math.round(maxOfMaxWinner) <= Math.round(maxOfTotalWinner) + Recommender.TIE_BAND_MINUTES);
    }

    @Test
    public void everyoneStartingAtTheSameStation_recommendsThatStationFirst() {
        List<Recommendation> r = recommender.recommend(
                Arrays.asList(at("민수", "강남"), at("지현", "강남"), at("도윤", "강남")), Criterion.TOTAL_TIME);
        assertEquals("강남", r.get(0).station);
        assertEquals(0.0, r.get(0).totalMinutes, 1e-6);
    }
}
