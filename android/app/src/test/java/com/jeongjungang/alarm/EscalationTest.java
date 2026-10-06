package com.jeongjungang.alarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EscalationTest {

    @Test
    public void escalationKeysDoNotCollideWithAlarmIds() {
        assertTrue(AlarmRingService.isEscalationKey(AlarmRingService.ESCALATION_PREFIX + "9b7e"));
        assertFalse(AlarmRingService.isEscalationKey("9b7e"));
        assertFalse(AlarmRingService.isEscalationKey(null));
    }

    @Test
    public void escalationTitle() {
        assertEquals("민수님이 아직 안 일어났어요", AlarmRingService.escalationTitle("민수"));
        assertEquals("친구님이 아직 안 일어났어요", AlarmRingService.escalationTitle(null));
    }

    @Test
    public void ttlAndRingLengthMatchSpec() {
        assertEquals(120_000L, AlarmRingService.ESCALATION_TTL_MS);
        assertTrue(AlarmRingService.ESCALATION_MAX_RING_MS < AlarmRingService.MAX_RING_MS);
    }
}
