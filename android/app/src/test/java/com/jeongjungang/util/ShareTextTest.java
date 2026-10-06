package com.jeongjungang.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.jeongjungang.data.repository.TransitData;
import com.jeongjungang.data.repository.TransitDataTest;
import com.jeongjungang.domain.recommend.Criterion;
import com.jeongjungang.domain.recommend.Participant;
import com.jeongjungang.domain.recommend.PersonTrip;
import com.jeongjungang.domain.recommend.Recommendation;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

public class ShareTextTest {

    private static List<Recommendation> results;

    @BeforeClass
    public static void recommend() throws IOException {
        TransitData data = TransitDataTest.loadFromAssetsDir();
        results = data.recommender.recommend(Arrays.asList(
                new Participant("민수", data.index.coordinateOf("홍대입구")),
                new Participant("지영", data.index.coordinateOf("노원")),
                new Participant("현우", data.index.coordinateOf("천호"))), Criterion.MAX_TIME);
    }

    @Test
    public void tripLabel() {
        assertEquals("민수 25분 · 환승 없음", ShareText.tripLabel(new PersonTrip("민수", 24.6, 0)));
        assertEquals("지영 35분 · 환승 2회", ShareText.tripLabel(new PersonTrip("지영", 35.2, 2)));
    }

    @Test
    public void stationLabelAddsSuffixAndLines() {
        Recommendation first = results.get(0);
        String label = ShareText.stationLabel(first);
        assertTrue(label, label.startsWith(first.station + "역 ("));
        assertTrue(label, label.endsWith("호선)"));
    }

    @Test
    public void forAllListsEveryCandidateInRankOrderWithNotice() {
        String text = ShareText.forAll(results);

        assertTrue(text.startsWith(ShareText.HEADER));
        assertTrue(text.endsWith(ShareText.APPROX_NOTICE));
        int last = -1;
        for (int i = 0; i < results.size(); i++) {
            Recommendation r = results.get(i);
            int at = text.indexOf((i + 1) + ". " + ShareText.stationLabel(r));
            assertTrue("순위 " + (i + 1) + " 누락:\n" + text, at > last);
            assertTrue(text.contains(r.reason));
            last = at;
        }
        for (PersonTrip t : results.get(0).trips) {
            assertTrue(text.contains(ShareText.tripLabel(t)));
        }
    }

    @Test
    public void forOneHasSingleCandidate() {
        String text = ShareText.forOne(results.get(0));
        assertTrue(text.contains(ShareText.stationLabel(results.get(0))));
        assertTrue(text.endsWith(ShareText.APPROX_NOTICE));
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyListIsRejected() {
        ShareText.forAll(Collections.<Recommendation>emptyList());
    }
}
