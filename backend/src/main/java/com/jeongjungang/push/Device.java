package com.jeongjungang.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 참가자의 푸시 수신 기기. 로그인이 없어서 참가자(토큰)마다 하나씩 둔다. */
@Entity
@Table(name = "devices")
public class Device {

    @Id
    @Column(name = "participant_id")
    private UUID participantId;

    @Column(name = "fcm_token", nullable = false, length = 512)
    private String fcmToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Column(name = "app_version")
    private String appVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Device() {
    }

    UUID participantId() {
        return participantId;
    }

    String fcmToken() {
        return fcmToken;
    }

    /** 책임 알람은 Android만 지원한다(CLAUDE.md). */
    public enum Platform {
        ANDROID
    }
}
