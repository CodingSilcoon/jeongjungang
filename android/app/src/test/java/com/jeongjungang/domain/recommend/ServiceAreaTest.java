package com.jeongjungang.domain.recommend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.jeongjungang.data.repository.TransitData;
import com.jeongjungang.data.repository.TransitDataTest;
import com.jeongjungang.domain.model.LatLng;
import com.jeongjungang.domain.transit.StationIndex;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

/** 번들 역 좌표로 지원 지역 3km 기준을 확인한다 (결정 근거로 쓴 지점들). */
public class ServiceAreaTest {

    private static StationIndex index;

    @BeforeClass
    public static void load() throws IOException {
        TransitData data = TransitDataTest.loadFromAssetsDir();
        index = data.index;
    }

    @Test
    public void seoulEdgesAreSupported() {
        assertTrue("가양(9호선만, 우장산 2.1km)", ServiceArea.isSupported(index, new LatLng(37.5614, 126.8545)));
        assertTrue("우이동(쌍문 2.6km)", ServiceArea.isSupported(index, new LatLng(37.6630, 127.0120)));
        assertTrue("위례(장지 1.6km)", ServiceArea.isSupported(index, new LatLng(37.4780, 127.1440)));
        assertTrue("개포동(분당선만, 대치 0.7km)", ServiceArea.isSupported(index, new LatLng(37.4891, 127.0663)));
    }

    @Test
    public void gyeonggiIsOutside() {
        assertFalse("판교(모란 4.6km)", ServiceArea.isSupported(index, new LatLng(37.3950, 127.1110)));
        assertFalse("일산(방화 9.8km)", ServiceArea.isSupported(index, new LatLng(37.6590, 126.7700)));
        assertFalse("수원역", ServiceArea.isSupported(index, new LatLng(37.2660, 127.0000)));
    }

    @Test
    public void outsideKeepsInputOrderAndMessageNamesPeople() {
        Participant hongdae = new Participant("민수", index.coordinateOf("홍대입구"));
        Participant ilsan = new Participant("지현", new LatLng(37.6590, 126.7700));
        Participant pangyo = new Participant("도윤", new LatLng(37.3950, 127.1110));

        List<Participant> out = ServiceArea.outside(index, Arrays.asList(ilsan, hongdae, pangyo));

        assertEquals(Arrays.asList(ilsan, pangyo), out);
        assertEquals("지현, 도윤님 출발지는 지원 지역 밖이에요. " + ServiceArea.MESSAGE, ServiceArea.messageFor(out));
        assertTrue(ServiceArea.outside(index, Arrays.asList(hongdae)).isEmpty());
    }

    @Test
    public void thresholdIsThreeKilometers() {
        assertEquals(3000, ServiceArea.MAX_STATION_DISTANCE_METERS, 0);
    }
}
