package com.jeongjungang.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.jeongjungang.data.repository.TransitData;
import com.jeongjungang.data.repository.TransitDataTest;
import com.jeongjungang.domain.model.LatLng;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

/** 번들 CSV로 서버 없는 대체 검색을 확인한다. */
public class StationGeocodeApiTest {

    private static TransitData data;
    private static StationGeocodeApi api;

    @BeforeClass
    public static void load() throws IOException {
        data = TransitDataTest.loadFromAssetsDir();
        api = new StationGeocodeApi(new StationGeocodeApi.DataProvider() {
            @Override
            public TransitData get() {
                return data;
            }
        });
    }

    @Test
    public void partialNameFindsStationWithLinesAndCoordinates() throws Exception {
        List<GeoPlace> items = api.search("홍대", 5);
        assertEquals("홍대입구역", items.get(0).name);
        assertEquals(GeoPlace.Type.PLACE, items.get(0).type);
        assertTrue(items.get(0).address, items.get(0).address.contains("2"));
        assertEquals(data.index.coordinateOf("홍대입구"), items.get(0).location);
    }

    @Test
    public void exactNameComesFirstAndStationSuffixIsIgnored() throws Exception {
        List<GeoPlace> items = api.search("강남역", 10);
        assertEquals("강남역", items.get(0).name);
        List<GeoPlace> noSuffix = api.search("강남", 10);
        assertEquals("강남역", noSuffix.get(0).name);
    }

    @Test
    public void prefixMatchBeatsMiddleMatch() throws Exception {
        List<GeoPlace> items = api.search("서울", 10);
        assertEquals("서울역", items.get(0).name);
        for (GeoPlace p : items) {
            assertTrue(p.name, p.name.contains("서울"));
        }
    }

    @Test
    public void sizeLimitsResults() throws Exception {
        assertTrue(api.search("입구", 10).size() > 2);
        assertEquals(2, api.search("입구", 2).size());
    }

    @Test
    public void noMatchIsEmpty() throws Exception {
        assertTrue(api.search("없는역이름", 5).isEmpty());
    }

    @Test
    public void tooShortQueryIsRejected() {
        try {
            api.search("홍", 5);
            fail();
        } catch (ApiException e) {
            assertEquals("VALIDATION_FAILED", e.code);
        }
    }

    @Test
    public void reverseNamesNearestStation() throws Exception {
        LatLng hongdae = data.index.coordinateOf("홍대입구");
        ReverseAddress near = api.reverse(new LatLng(hongdae.lat + 0.001, hongdae.lng));
        assertTrue(near.displayText(), near.displayText().startsWith("홍대입구역 근처 (약 "));
        assertNull(near.roadAddress);
    }

    @Test
    public void labels() {
        assertEquals("2·6호선", StationGeocodeApi.linesLabel(Arrays.asList(2, 6)));
        assertNull(StationGeocodeApi.linesLabel(Arrays.<Integer>asList()));
        assertEquals("10m", StationGeocodeApi.distanceLabel(3));
        assertEquals("350m", StationGeocodeApi.distanceLabel(346));
        assertEquals("1.2km", StationGeocodeApi.distanceLabel(1234));
    }

    @Test
    public void loadFailureBecomesApiException() {
        StationGeocodeApi broken = new StationGeocodeApi(new StationGeocodeApi.DataProvider() {
            @Override
            public TransitData get() throws IOException {
                throw new IOException("assets 없음");
            }
        });
        try {
            broken.search("강남", 5);
            fail();
        } catch (ApiException e) {
            assertEquals(ApiException.BAD_RESPONSE, e.code);
        }
    }
}
