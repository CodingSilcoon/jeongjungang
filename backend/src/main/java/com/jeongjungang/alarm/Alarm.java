package com.jeongjungang.alarm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 참가자 한 명의 출발 알람. 상태 전이는 docs/API.md 7절 "알람 상태"를 따른다. */
@Entity
@Table(name = "alarms")
public class Alarm {

    @Id
    private UUID id;

    @Column(name = "meeting_id", nullable = false)
    private UUID meetingId;

    @Column(name = "participant_id", nullable = false, unique = true)
    private UUID participantId;

    @Column(name = "fire_at", nullable = false)
    private Instant fireAt;

    @Column(name = "grace_until", nullable = false)
    private Instant graceUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlarmStatus status;

    @Column(name = "ringing_at")
    private Instant ringingAt;

    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "dismiss_source")
    private DismissSource dismissSource;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Alarm() {
    }

    static Alarm schedule(UUID meetingId, UUID participantId, AlarmTimes times, Instant now) {
        Alarm a = new Alarm();
        a.id = UUID.randomUUID();
        a.meetingId = meetingId;
        a.participantId = participantId;
        a.reschedule(times, now);
        return a;
    }

    /** 새 시각으로 다시 건다. 이전 울림·끔·에스컬레이션 기록은 지운다(약속 시간이 바뀌면 새 알람이다). */
    void reschedule(AlarmTimes times, Instant now) {
        this.fireAt = times.fireAt();
        this.graceUntil = times.graceUntil();
        this.status = AlarmStatus.SCHEDULED;
        this.ringingAt = null;
        this.dismissedAt = null;
        this.dismissSource = null;
        this.escalatedAt = null;
        this.updatedAt = now;
    }

    boolean hasTimes(AlarmTimes times) {
        return fireAt.equals(times.fireAt()) && graceUntil.equals(times.graceUntil());
    }

    void cancel(Instant now) {
        this.status = AlarmStatus.CANCELLED;
        this.updatedAt = now;
    }

    /** 상태 표시용. SCHEDULED일 때만 바꾸고, 그 외에는 무시한다. */
    void markRinging(Instant now) {
        if (status == AlarmStatus.SCHEDULED) {
            this.status = AlarmStatus.RINGING;
            this.ringingAt = now;
            this.updatedAt = now;
        }
    }

    /** 멱등. 이미 ESCALATED·CANCELLED면 되돌리지 않고 그 상태를 돌려준다. */
    AlarmStatus dismiss(DismissSource source, Instant now) {
        if (status.isActive()) {
            this.status = AlarmStatus.DISMISSED;
            this.dismissedAt = now;
            this.dismissSource = source;
            this.updatedAt = now;
        }
        return status;
    }

    /** 유예시간이 지났는데 아직 안 껐으면 ESCALATED로 바꾸고 true. 알람당 한 번만 true가 된다. */
    boolean escalateIfDue(Instant now) {
        if (!status.isActive() || now.isBefore(graceUntil)) {
            return false;
        }
        this.status = AlarmStatus.ESCALATED;
        this.escalatedAt = now;
        this.updatedAt = now;
        return true;
    }

    public UUID id() {
        return id;
    }

    UUID meetingId() {
        return meetingId;
    }

    UUID participantId() {
        return participantId;
    }

    Instant fireAt() {
        return fireAt;
    }

    Instant graceUntil() {
        return graceUntil;
    }

    AlarmStatus status() {
        return status;
    }

    Instant escalatedAt() {
        return escalatedAt;
    }
}
