package com.jeongjungang.domain.transit;

import static org.junit.Assert.assertEquals;

import com.jeongjungang.domain.transit.TransitCsv.CoordRow;
import com.jeongjungang.domain.transit.TransitCsv.IntervalRow;
import com.jeongjungang.domain.transit.TransitCsv.TransferRow;
import java.io.StringReader;
import java.util.List;
import org.junit.Test;

public class TransitCsvTest {

    private static final double DELTA = 1e-6;

    @Test
    public void parseMinutes_readsMinuteSecond() {
        assertEquals(2.0, TransitCsv.parseMinutes("02:00"), DELTA);
        assertEquals(2 + 13 / 60.0, TransitCsv.parseMinutes("02:13"), DELTA);
        assertEquals(0.0, TransitCsv.parseMinutes("00:00"), DELTA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseMinutes_rejectsBadFormat() {
        TransitCsv.parseMinutes("2분");
    }

    @Test
    public void parseIntervals_skipsHeaderAndNormalizesNames() throws Exception {
        String csv = "﻿연번,호선,역명,소요시간,역간거리(km),호선별누계(km)\n"
                + "1,1,서울역,00:00,0,0\n"
                + "2,1,시청,02:00,1.1,1.1\n"
                + "3,4,미아사거리 ,01:30,1.2,5.5\n";
        List<IntervalRow> rows = TransitCsv.parseIntervals(new StringReader(csv));
        assertEquals(3, rows.size());
        assertEquals("서울", rows.get(0).station);
        assertEquals(1, rows.get(0).line);
        assertEquals(0.0, rows.get(0).km, DELTA);
        assertEquals(2.0, rows.get(1).minutes, DELTA);
        assertEquals("미아사거리", rows.get(2).station);
        assertEquals(4, rows.get(2).line);
    }

    @Test
    public void parseCoords_readsLatLng() throws Exception {
        String csv = "연번,호선,고유역번호(외부역코드),역명,위도,경도,작성일자\n"
                + "1,1,150,서울,37.55315,126.972533,1974-02-28\n";
        List<CoordRow> rows = TransitCsv.parseCoords(new StringReader(csv));
        assertEquals(1, rows.size());
        assertEquals("서울", rows.get(0).station);
        assertEquals(37.55315, rows.get(0).lat, DELTA);
        assertEquals(126.972533, rows.get(0).lng, DELTA);
    }

    @Test
    public void parseTransfers_marksNonSeoulMetroLines() throws Exception {
        String csv = "연번,호선,환승역명,환승노선,환승거리,환승소요시간\n"
                + "1,1,서울역,4호선,159,02:13\n"
                + "2,1,서울역,공항철도,309,04:18\n";
        List<TransferRow> rows = TransitCsv.parseTransfers(new StringReader(csv));
        assertEquals(2, rows.size());
        assertEquals(1, rows.get(0).line);
        assertEquals(4, rows.get(0).toLine);
        assertEquals("서울", rows.get(0).station);
        assertEquals(2 + 13 / 60.0, rows.get(0).minutes, DELTA);
        assertEquals(-1, rows.get(1).toLine);
    }

    @Test
    public void blankLinesAreIgnored() throws Exception {
        String csv = "연번,호선,환승역명,환승노선,환승거리,환승소요시간\n\n1,1,서울역,4호선,159,02:13\n\n";
        assertEquals(1, TransitCsv.parseTransfers(new StringReader(csv)).size());
    }
}
