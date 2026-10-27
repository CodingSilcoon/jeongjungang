package com.jeongjungang.push;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class PushPayloadTest {

    private static Map<String, String> data(String... kv) {
        Map<String, String> m = new HashMap<String, String>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    public void escalation_parsesServerFields() {
        // 서버 PushNotifier가 보내는 형식 그대로 (Instant.toString)
        PushPayload p = PushPayload.parse(data("type", "ESCALATION", "meetingId", "m1", "alarmId", "a1",
                "sleeperNickname", "민수", "fireAt", "2026-10-23T00:33:00Z",
                "escalatedAt", "2026-10-23T00:34:00.231Z"));

        assertEquals(PushPayload.Type.ESCALATION, p.type);
        assertEquals("m1", p.meetingId);
        assertEquals("a1", p.alarmId);
        assertEquals("민수", p.sleeperNickname);
        assertEquals(1792715640231L, p.escalatedAtMillis);
    }

    @Test
    public void syncAndCancel_needOnlyMeetingId() {
        assertEquals(PushPayload.Type.ALARM_SYNC,
                PushPayload.parse(data("type", "ALARM_SYNC", "meetingId", "m1")).type);
        assertEquals(PushPayload.Type.ALARM_CANCELLED,
                PushPayload.parse(data("type", "ALARM_CANCELLED", "meetingId", "m1")).type);
    }

    @Test
    public void unknownOrBrokenMessages_areIgnored() {
        assertNull(PushPayload.parse(null));
        assertNull(PushPayload.parse(data("type", "SOMETHING_NEW", "meetingId", "m1")));
        assertNull(PushPayload.parse(data("meetingId", "m1")));
        assertNull(PushPayload.parse(data("type", "ALARM_SYNC", "meetingId", " ")));
        assertNull(PushPayload.parse(data("type", "ESCALATION", "meetingId", "m1", "escalatedAt", "2026-10-23T00:34:00Z")));
        assertNull(PushPayload.parse(data("type", "ESCALATION", "meetingId", "m1", "alarmId", "a1",
                "escalatedAt", "어제")));
    }
}
