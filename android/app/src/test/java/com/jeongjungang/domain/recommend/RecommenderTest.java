package com.jeongjungang.domain.recommend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.jeongjungang.domain.model.LatLng;
import com.jeongjungang.domain.transit.StationIndex;
import com.jeongjungang.domain.transit.TransitCsv.CoordRow;
import com.jeongjungang.domain.transit.TransitCsv.IntervalRow;
import com.jeongjungang.domain.transit.TransitCsv.TransferRow;
import com.jeongjungang.domain.transit.TransitGraph;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class RecommenderTest {

    private static final double DELTA = 1e-6;
    private static final double LAT = 37.5;
    /** 경도 0.01도 간격(약 0.88km)으로 일렬 배치한 역 S1..S7, 이웃 역 사이 2분. */
    private static final int LINE_STATIONS = 7;

    private static LatLng at(int stationNumber) {
        return new LatLng(LAT, 127.0 + 0.01 * (stationNumber - 1));
    }

    private static List<IntervalRow> lineRows() {
        List<IntervalRow> rows = new ArrayList<IntervalRow>();
        for (int i = 1; i <= LINE_STATIONS; i++) {
            rows.add(new IntervalRow(1, "S" + i, i == 1 ? 0 : 2, i == 1 ? 0 : 1));
        }
        return rows;
    }

    private static List<CoordRow> lineCoords() {
        List<CoordRow> coords = new ArrayList<CoordRow>();
        for (int i = 1; i <= LINE_STATIONS; i++) {
            coords.add(new CoordRow(1, "S" + i, at(i).lat, at(i).lng));
        }
        return coords;
    }

    private static Recommender lineRecommender(int poolSize) {
        TransitGraph graph = TransitGraph.build(lineRows(), Collections.<TransferRow>emptyList());
        return new Recommender(graph, new StationIndex(lineCoords(), graph), poolSize, 3);
    }

    private static List<Participant> twoWestOneEast() {
        return Arrays.asList(
                new Participant("민수", at(1)), new Participant("지현", at(1)), new Participant("도윤", at(7)));
    }

    @Test
    public void totalTimeCriterion_prefersStationNearTheCluster() {
        List<Recommendation> r = lineRecommender(7).recommend(twoWestOneEast(), Criterion.TOTAL_TIME);
        assertEquals("S1", r.get(0).station);
        assertEquals(12.0, r.get(0).totalMinutes, DELTA);
        assertEquals(12.0, r.get(0).maxMinutes, DELTA);
    }

    @Test
    public void maxTimeCriterion_prefersTheMiddleStation() {
        List<Recommendation> r = lineRecommender(7).recommend(twoWestOneEast(), Criterion.MAX_TIME);
        assertEquals("S4", r.get(0).station);
        assertEquals(6.0, r.get(0).maxMinutes, DELTA);
        assertEquals(18.0, r.get(0).totalMinutes, DELTA);
    }

    @Test
    public void results_areSortedByTheChosenCriterion() {
        List<Recommendation> r = lineRecommender(7).recommend(twoWestOneEast(), Criterion.TOTAL_TIME);
        for (int i = 1; i < r.size(); i++) {
            assertTrue(Math.round(r.get(i - 1).totalMinutes) <= Math.round(r.get(i).totalMinutes));
        }
    }

    @Test
    public void returnsAtMostThreeCandidates() {
        assertEquals(3, lineRecommender(7).recommend(twoWestOneEast(), Criterion.TOTAL_TIME).size());
    }

    @Test
    public void poolSize_limitsWhichStationsCanBeRecommended() {
        // 중앙값이 S1이므로 풀 크기 3이면 S1..S3만 후보가 된다.
        List<Recommendation> r = lineRecommender(3).recommend(twoWestOneEast(), Criterion.MAX_TIME);
        assertEquals("S3", r.get(0).station);
        assertEquals(3, r.size());
    }

    @Test
    public void tripsKeepInputOrderAndNames() {
        Recommendation top = lineRecommender(7).recommend(twoWestOneEast(), Criterion.MAX_TIME).get(0);
        assertEquals(3, top.trips.size());
        assertEquals("민수", top.trips.get(0).name);
        assertEquals("지현", top.trips.get(1).name);
        assertEquals("도윤", top.trips.get(2).name);
        assertEquals(6.0, top.trips.get(0).minutes, DELTA);
        assertEquals(0, top.maxTransfers);
    }

    @Test
    public void everyRecommendationCarriesLinesAndAReason() {
        Recommendation top = lineRecommender(7).recommend(twoWestOneEast(), Criterion.MAX_TIME).get(0);
        assertEquals(Arrays.asList(1), top.lines);
        assertFalse(top.reason.isEmpty());
    }

    @Test
    public void equalTotals_fallBackToSmallerMaximum() {
        // 두 명이 S1, S7에 있으면 S1..S7 모든 역의 합이 12분으로 같고, 최대는 가운데 역이 가장 작다.
        List<Participant> two = Arrays.asList(new Participant("민수", at(1)), new Participant("도윤", at(7)));
        List<Recommendation> r = lineRecommender(7).recommend(two, Criterion.TOTAL_TIME);
        assertEquals("S4", r.get(0).station);
    }

    @Test
    public void stationsNobodyCanReachTogetherAreDropped() {
        List<IntervalRow> rows = new ArrayList<IntervalRow>(lineRows());
        rows.add(new IntervalRow(2, "고립1", 0, 0));
        rows.add(new IntervalRow(2, "고립2", 2, 1));
        List<CoordRow> coords = new ArrayList<CoordRow>(lineCoords());
        coords.add(new CoordRow(2, "고립1", LAT, 127.03));
        coords.add(new CoordRow(2, "고립2", LAT, 127.031));
        TransitGraph graph = TransitGraph.build(rows, Collections.<TransferRow>emptyList());
        Recommender rec = new Recommender(graph, new StationIndex(coords, graph), 20, 3);
        for (Recommendation r : rec.recommend(twoWestOneEast(), Criterion.TOTAL_TIME)) {
            assertFalse(r.station.startsWith("고립"));
        }
    }

    @Test
    public void whenParticipantsShareNoReachableStation_resultIsEmpty() {
        List<IntervalRow> rows = Arrays.asList(
                new IntervalRow(1, "W1", 0, 0), new IntervalRow(1, "W2", 2, 1),
                new IntervalRow(2, "E1", 0, 0), new IntervalRow(2, "E2", 2, 1));
        List<CoordRow> coords = Arrays.asList(
                new CoordRow(1, "W1", LAT, 127.00), new CoordRow(1, "W2", LAT, 127.01),
                new CoordRow(2, "E1", LAT, 127.50), new CoordRow(2, "E2", LAT, 127.51));
        TransitGraph graph = TransitGraph.build(rows, Collections.<TransferRow>emptyList());
        Recommender rec = new Recommender(graph, new StationIndex(coords, graph), 4, 1);
        List<Participant> split = Arrays.asList(
                new Participant("민수", new LatLng(LAT, 127.00)), new Participant("지현", new LatLng(LAT, 127.51)));
        assertTrue(rec.recommend(split, Criterion.TOTAL_TIME).isEmpty());
    }

    @Test
    public void singleParticipant_getsStationsAroundThem() {
        List<Recommendation> r = lineRecommender(7).recommend(
                Arrays.asList(new Participant("민수", at(4))), Criterion.TOTAL_TIME);
        assertEquals("S4", r.get(0).station);
        assertEquals(0.0, r.get(0).totalMinutes, DELTA);
    }

    private static Recommendation fakeCandidate(String station, double minutes, int transfers) {
        return new Recommendation(station, Arrays.asList(1),
                Arrays.asList(new PersonTrip("민수", minutes, transfers)), "");
    }

    private static String firstAfterSorting(Criterion criterion, Recommendation... candidates) {
        return order(criterion, candidates).get(0);
    }

    private static List<String> order(Criterion criterion, Recommendation... candidates) {
        List<String> names = new ArrayList<String>();
        for (Recommendation r : Recommender.rank(Arrays.asList(candidates), criterion)) {
            names.add(r.station);
        }
        return names;
    }

    @Test
    public void sameWholeMinutes_fewerTransfersComeFirst() {
        // 4.9분과 5.0분은 화면에서 같은 "5분"이므로 환승이 적은 쪽이 앞선다.
        Recommendation fasterButTransfers = fakeCandidate("환승많음", 4.9, 3);
        Recommendation direct = fakeCandidate("직통", 5.0, 0);
        assertEquals("직통", firstAfterSorting(Criterion.TOTAL_TIME, fasterButTransfers, direct));
        assertEquals("직통", firstAfterSorting(Criterion.MAX_TIME, fasterButTransfers, direct));
    }

    @Test
    public void withinTwoMinutes_fewerTransfersComeFirst() {
        // 4분(환승 3)과 6분(직통)은 2분 차이라 한 묶음 → 직통이 앞선다
        Recommendation faster = fakeCandidate("빠름", 4.0, 3);
        Recommendation direct = fakeCandidate("직통", 6.0, 0);
        assertEquals("직통", firstAfterSorting(Criterion.TOTAL_TIME, faster, direct));
        assertEquals("직통", firstAfterSorting(Criterion.MAX_TIME, faster, direct));
    }

    @Test
    public void moreThanTwoMinutesFaster_winsEvenWithMoreTransfers() {
        Recommendation faster = fakeCandidate("빠름", 2.4, 3);   // 화면 2분
        Recommendation direct = fakeCandidate("직통", 5.0, 0);   // 화면 5분, 3분 차이
        assertEquals("빠름", firstAfterSorting(Criterion.TOTAL_TIME, direct, faster));
    }

    @Test
    public void tieBandIsAnchoredToTheFastestNotChained() {
        // 27(환승 3) · 29(환승 2)는 한 묶음, 30(직통)은 27보다 3분 느려서 다음 묶음.
        // 이웃끼리 이으면(29→30) 직통이 1위가 됐겠지만, 묶음 기준은 가장 빠른 후보다.
        List<String> got = order(Criterion.MAX_TIME,
                fakeCandidate("직통", 30, 0), fakeCandidate("환승셋", 27, 3), fakeCandidate("환승둘", 29, 2));
        assertEquals(Arrays.asList("환승둘", "환승셋", "직통"), got);
    }

    @Test
    public void tieBandConstantIsTwoMinutes() {
        assertEquals(2, Recommender.TIE_BAND_MINUTES);
    }

    @Test
    public void allTiesFallBackToStationNameForStableOrder() {
        assertEquals("가", firstAfterSorting(Criterion.TOTAL_TIME, fakeCandidate("나", 5, 0), fakeCandidate("가", 5, 0)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyParticipants_throw() {
        lineRecommender(7).recommend(Collections.<Participant>emptyList(), Criterion.TOTAL_TIME);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullCriterion_throws() {
        lineRecommender(7).recommend(twoWestOneEast(), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonPositivePoolSize_throws() {
        lineRecommender(0);
    }
}
