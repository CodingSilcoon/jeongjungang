package com.jeongjungang.domain.transit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StationNamesTest {

    @Test
    public void stripsTrailingStationSuffix() {
        assertEquals("서울", StationNames.normalize("서울역"));
        assertEquals("신내", StationNames.normalize("신내역"));
    }

    @Test
    public void trimsWhitespace() {
        assertEquals("미아사거리", StationNames.normalize("미아사거리 "));
    }

    @Test
    public void keepsNamesWithDigitsAndInnerYeok() {
        assertEquals("을지로3가", StationNames.normalize("을지로3가"));
        assertEquals("동대문역사문화공원", StationNames.normalize("동대문역사문화공원"));
    }

    @Test
    public void mapsAliasesToCanonicalName() {
        assertEquals("이수", StationNames.normalize("총신대입구"));
        assertEquals("자양", StationNames.normalize("뚝섬유원지"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullThrows() {
        StationNames.normalize(null);
    }
}
