package com.jeongjungang.alarm;

import java.time.Instant;
import java.util.UUID;

/** 에스컬레이션 큐 항목: 이 알람을 graceUntil에 확인한다. */
record DueAlarm(UUID alarmId, Instant graceUntil) {
}
