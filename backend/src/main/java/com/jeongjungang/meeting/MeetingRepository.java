package com.jeongjungang.meeting;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MeetingRepository extends JpaRepository<Meeting, UUID> {

    Optional<Meeting> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);

    /** 참가·탈퇴·수정은 약속 행을 잠그고 한다. 인원 상한과 방장 넘기기가 동시에 꼬이지 않게 하기 위함이다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Meeting m where m.id = :id")
    Optional<Meeting> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Meeting m where m.inviteCode = :code")
    Optional<Meeting> findByInviteCodeForUpdate(@Param("code") String code);

    /** 기한이 지난 약속을 지운다. 참가자는 외래키 ON DELETE CASCADE로 함께 지워진다. */
    @Modifying
    @Query("delete from Meeting m where m.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
