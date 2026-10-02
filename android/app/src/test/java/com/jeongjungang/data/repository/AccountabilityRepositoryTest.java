package com.jeongjungang.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.jeongjungang.data.remote.ApiException;
import org.junit.Test;

/** 기기 시계 보정과 재전송 판단. 저장소·Keystore가 필요한 부분은 에뮬레이터에서 확인했다. */
public class AccountabilityRepositoryTest {

    private static final long MIN = 60_000L;

    @Test
    public void deviceClockFastMeansLaterDeviceTime() {
        long serverFireAt = 1_000 * MIN;
        long serverNow = 900 * MIN;
        // 기기 시계가 서버보다 3분 빠르면, 기기 기준 3분 뒤에 잡아야 실제 시각에 울린다
        assertEquals(serverFireAt + 3 * MIN,
                AccountabilityRepository.toDeviceTime(serverFireAt, serverNow, serverNow + 3 * MIN));
        // 2분 느리면 2분 앞당긴다
        assertEquals(serverFireAt - 2 * MIN,
                AccountabilityRepository.toDeviceTime(serverFireAt, serverNow, serverNow - 2 * MIN));
    }

    @Test
    public void noServerTimeMeansNoCorrection() {
        assertEquals(12345L, AccountabilityRepository.toDeviceTime(12345L, 0, 99999L));
    }

    @Test
    public void retryOnlyWhenItCouldSucceedLater() {
        assertTrue(AccountabilityRepository.isRetryable(new ApiException(ApiException.NETWORK, 0, "", 0, null)));
        assertTrue(AccountabilityRepository.isRetryable(new ApiException(ApiException.RATE_LIMITED, 429, "", 5, null)));
        assertTrue(AccountabilityRepository.isRetryable(new ApiException("INTERNAL_ERROR", 500, "", 0, null)));
        assertTrue(AccountabilityRepository.isRetryable(new ApiException(ApiException.BAD_RESPONSE, 200, "", 0, null)));
        assertFalse(AccountabilityRepository.isRetryable(new ApiException("ALARM_NOT_FOUND", 404, "", 0, null)));
        assertFalse(AccountabilityRepository.isRetryable(new ApiException("UNAUTHORIZED", 401, "", 0, null)));
        assertFalse(AccountabilityRepository.isRetryable(new ApiException("FORBIDDEN", 403, "", 0, null)));
    }
}
