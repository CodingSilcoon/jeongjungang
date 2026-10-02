package com.jeongjungang.domain.recommend;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import org.junit.Test;

public class ReasonWriterTest {

    private static PersonTrip trip(String name, double minutes, int transfers) {
        return new PersonTrip(name, minutes, transfers);
    }

    @Test
    public void balancedTrips_sayEveryoneWithinRoundedUpTenMinutes() {
        String reason = ReasonWriter.write(Arrays.asList(
                trip("민수", 25, 0), trip("지현", 35, 0), trip("도윤", 29, 0)));
        assertEquals("전원 40분 이내, 환승 없음", reason);
    }

    @Test
    public void oneSlowPerson_isCalledOutByName() {
        String reason = ReasonWriter.write(Arrays.asList(
                trip("민수", 29, 1), trip("지현", 42, 1), trip("도윤", 20, 0)));
        assertEquals("지현님만 42분으로 길어요", reason);
    }

    @Test
    public void outlierGapBelowTenMinutes_isNotCalledOut() {
        String reason = ReasonWriter.write(Arrays.asList(
                trip("민수", 30, 0), trip("지현", 39, 0), trip("도윤", 20, 0)));
        assertEquals("전원 40분 이내, 환승 없음", reason);
    }

    @Test
    public void transferCount_usesMaximumAcrossPeople() {
        String reason = ReasonWriter.write(Arrays.asList(
                trip("민수", 30, 0), trip("지현", 33, 2), trip("도윤", 31, 1)));
        assertEquals("전원 40분 이내, 환승 최대 2회", reason);
    }

    @Test
    public void exactTenMinuteBoundary_staysInThatBucket() {
        String reason = ReasonWriter.write(Arrays.asList(
                trip("민수", 40, 0), trip("지현", 38, 0), trip("도윤", 35, 0)));
        assertEquals("전원 40분 이내, 환승 없음", reason);
    }

    @Test
    public void justOverBoundary_movesToNextBucket() {
        String reason = ReasonWriter.write(Arrays.asList(
                trip("민수", 40.2, 0), trip("지현", 38, 0), trip("도윤", 35, 0)));
        assertEquals("전원 50분 이내, 환승 없음", reason);
    }

    @Test
    public void veryLongTrip_namesTheLongestPersonInsteadOfBucket() {
        String reason = ReasonWriter.write(Arrays.asList(
                trip("민수", 90, 0), trip("지현", 92, 0), trip("도윤", 95, 1)));
        assertEquals("가장 오래 걸리는 도윤님 95분, 환승 최대 1회", reason);
    }

    @Test
    public void twoPeople_neverTriggerOutlierCallOut() {
        String reason = ReasonWriter.write(Arrays.asList(trip("민수", 20, 0), trip("지현", 50, 0)));
        assertEquals("전원 50분 이내, 환승 없음", reason);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyTrips_throw() {
        ReasonWriter.write(Arrays.<PersonTrip>asList());
    }
}
