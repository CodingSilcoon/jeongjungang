package com.jeongjungang.meeting;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

    Optional<Participant> findByTokenHash(String tokenHash);

    /** 들어온 순서. 응답의 participants 순서이자 다음 방장을 정하는 기준이다. */
    List<Participant> findByMeetingIdOrderByJoinOrderAsc(UUID meetingId);

    long countByMeetingId(UUID meetingId);

    @Query("select coalesce(max(p.joinOrder), 0) from Participant p where p.meetingId = :meetingId")
    int maxJoinOrder(@Param("meetingId") UUID meetingId);
}
