package com.jeongjungang.alarm;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 알람 행은 바꾸기 전에 잠근다. 끔 보고·에스컬레이션·시각 재계산이 같은 알람을 동시에 건드려도
 * 한쪽이 끝난 뒤의 상태를 다른 쪽이 보게 하기 위함이다. 잠금 순서는 항상 약속 → 알람이다.
 */
interface AlarmRepository extends JpaRepository<Alarm, UUID> {

    Optional<Alarm> findByParticipantId(UUID participantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Alarm a where a.id = :id")
    Optional<Alarm> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Alarm a where a.meetingId = :meetingId")
    List<Alarm> findByMeetingIdForUpdate(@Param("meetingId") UUID meetingId);

    /** 에스컬레이션 큐를 DB 기준으로 다시 채울 때 쓴다. */
    @Query("select new com.jeongjungang.alarm.DueAlarm(a.id, a.graceUntil) from Alarm a"
            + " where a.status in (com.jeongjungang.alarm.AlarmStatus.SCHEDULED,"
            + " com.jeongjungang.alarm.AlarmStatus.RINGING)")
    List<DueAlarm> findActive();
}
