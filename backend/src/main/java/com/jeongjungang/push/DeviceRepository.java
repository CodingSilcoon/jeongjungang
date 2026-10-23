package com.jeongjungang.push;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DeviceRepository extends JpaRepository<Device, UUID> {

    List<Device> findByParticipantIdIn(Collection<UUID> participantIds);

    @Modifying
    @Query("delete from Device d where d.fcmToken = :fcmToken")
    int deleteByFcmToken(@Param("fcmToken") String fcmToken);

    /** 없으면 만들고 있으면 갱신. 같은 참가자의 등록이 동시에 와도 기본키 충돌로 500이 나지 않는다. */
    @Modifying
    @Query(value = """
            insert into devices (participant_id, fcm_token, platform, app_version, updated_at)
            values (:participantId, :fcmToken, :platform, :appVersion, :now)
            on conflict (participant_id) do update
            set fcm_token = excluded.fcm_token, platform = excluded.platform,
                app_version = excluded.app_version, updated_at = excluded.updated_at
            """, nativeQuery = true)
    void upsert(@Param("participantId") UUID participantId, @Param("fcmToken") String fcmToken,
                @Param("platform") String platform, @Param("appVersion") String appVersion,
                @Param("now") Instant now);
}
