package com.jeongjungang.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.jeongjungang.data.remote.ApiException;
import com.jeongjungang.data.remote.meeting.AccountabilityApi;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class DeviceRegistrationTest {

    /** 등록 요청만 기록한다. failFor에 있는 참가자 토큰은 그 예외로 실패한다. */
    private static final class FakeApi implements AccountabilityApi {
        final List<String> registered = new ArrayList<String>();
        final Map<String, ApiException> failFor = new HashMap<String, ApiException>();

        @Override
        public void registerDevice(String token, String fcmToken, String appVersion) throws ApiException {
            ApiException e = failFor.get(token);
            if (e != null) {
                throw e;
            }
            registered.add(token + "=" + fcmToken);
        }

        @Override
        public void reportTravel(String participantId, String token, String placeName, double minutes,
                                 int transfers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void enable(String meetingId, String token, int gracePeriodSec, int marginMinutes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void disable(String meetingId, String token) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MyAlarm myAlarm(String meetingId, String token) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void ringing(String alarmId, String token) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AlarmStatus dismiss(String alarmId, String token, long dismissedAtMillis, DismissSource source) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class MemoryStore implements DeviceRegistration.Store {
        final Map<String, String> map = new HashMap<String, String>();

        @Override
        public String registeredToken(String meetingId) {
            return map.get(meetingId);
        }

        @Override
        public void markRegistered(String meetingId, String fcmToken) {
            map.put(meetingId, fcmToken);
        }

        @Override
        public void retainOnly(Collection<String> meetingIds) {
            Set<String> keep = new HashSet<String>(meetingIds);
            map.keySet().retainAll(keep);
        }
    }

    private final FakeApi api = new FakeApi();
    private final MemoryStore store = new MemoryStore();
    private final List<ParticipantTokenStore.Entry> members = new ArrayList<ParticipantTokenStore.Entry>();

    private DeviceRegistration registration() {
        return new DeviceRegistration(api, () -> new ArrayList<ParticipantTokenStore.Entry>(members), store, "1.0");
    }

    private void join(String meetingId) {
        members.add(new ParticipantTokenStore.Entry(meetingId, "p-" + meetingId, "t-" + meetingId));
    }

    @Test
    public void registersEveryMeeting_onceperToken() {
        join("m1");
        join("m2");

        assertTrue(registration().registerAll("fcm-1"));
        assertTrue(registration().registerAll("fcm-1"));

        assertEquals(List.of("t-m1=fcm-1", "t-m2=fcm-1"), api.registered);
    }

    @Test
    public void newToken_registersAgain_newMeeting_registersOnlyThatOne() {
        join("m1");
        registration().registerAll("fcm-1");
        join("m2");
        registration().registerAll("fcm-1");
        registration().registerAll("fcm-2");

        assertEquals(List.of("t-m1=fcm-1", "t-m2=fcm-1", "t-m1=fcm-2", "t-m2=fcm-2"), api.registered);
    }

    @Test
    public void networkFailure_asksForRetry_andRetriesOnlyThatMeeting() {
        join("m1");
        join("m2");
        api.failFor.put("t-m2", new ApiException(ApiException.NETWORK, 0, "오프라인", 0, null));

        assertFalse(registration().registerAll("fcm-1"));
        api.failFor.clear();
        assertTrue(registration().registerAll("fcm-1"));

        assertEquals(List.of("t-m1=fcm-1", "t-m2=fcm-1"), api.registered);
    }

    @Test
    public void goneMeeting_isNotRetried() {
        join("m1");
        api.failFor.put("t-m1", new ApiException("UNAUTHORIZED", 401, "다시 참가해 주세요", 0, null));

        assertTrue(registration().registerAll("fcm-1"));
    }

    @Test
    public void leftMeetings_areForgotten() {
        join("m1");
        registration().registerAll("fcm-1");
        members.clear();

        registration().registerAll("fcm-1");

        assertTrue(store.map.isEmpty());
    }

    @Test
    public void withoutServerOrToken_doesNothing() {
        join("m1");

        assertTrue(new DeviceRegistration(null, () -> members, store, "1.0").registerAll("fcm-1"));
        assertTrue(registration().registerAll(""));
        assertTrue(api.registered.isEmpty());
    }
}
