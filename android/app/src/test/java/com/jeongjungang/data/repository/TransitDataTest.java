package com.jeongjungang.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.jeongjungang.domain.model.LatLng;
import com.jeongjungang.domain.recommend.Criterion;
import com.jeongjungang.domain.recommend.Participant;
import com.jeongjungang.domain.recommend.Recommendation;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

/** 번들 CSV를 앱과 같은 경로(TransitData.load)로 읽는다. 모듈 디렉터리에서 실행한다. */
public class TransitDataTest {

    private static final String DIR = "src/main/assets/data/";
    private static final List<String> opened = new ArrayList<String>();
    private static TransitData data;

    /** 다른 테스트에서도 실제 데이터가 필요할 때 쓴다. */
    public static TransitData loadFromAssetsDir() throws IOException {
        return TransitData.load(new TransitData.Source() {
            @Override
            public Reader open(String fileName) throws IOException {
                opened.add(fileName);
                return new BufferedReader(new InputStreamReader(
                        Files.newInputStream(Paths.get(DIR + fileName)), StandardCharsets.UTF_8));
            }
        });
    }

    @BeforeClass
    public static void load() throws IOException {
        opened.clear();
        data = loadFromAssetsDir();
    }

    @Test
    public void readsAllThreeBundledFiles() {
        assertEquals(Arrays.asList(TransitData.INTERVALS_FILE, TransitData.COORDS_FILE, TransitData.TRANSFERS_FILE),
                opened);
        assertTrue(data.graph.nodeCount() > 0);
        assertTrue(data.index.stationCount() > 0);
    }

    @Test
    public void recommenderWorksAndEveryResultHasCoordinates() {
        List<Participant> people = Arrays.asList(
                new Participant("민수", data.index.coordinateOf("홍대입구")),
                new Participant("지영", data.index.coordinateOf("노원")),
                new Participant("현우", data.index.coordinateOf("천호")));

        List<Recommendation> results = data.recommender.recommend(people, Criterion.MAX_TIME);

        assertFalse(results.isEmpty());
        for (Recommendation r : results) {
            LatLng coord = data.index.coordinateOf(r.station);
            assertNotNull(r.station, coord);
        }
    }

    @Test(expected = IOException.class)
    public void missingFileFails() throws IOException {
        TransitData.load(new TransitData.Source() {
            @Override
            public Reader open(String fileName) throws IOException {
                throw new IOException("없음: " + fileName);
            }
        });
    }
}
