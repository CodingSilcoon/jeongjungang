package com.jeongjungang.meeting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 약속 참가자. 토큰 원문은 갖지 않고 해시만 저장한다. */
@Entity
@Table(name = "participants")
public class Participant {

    @Id
    private UUID id;

    @Column(name = "meeting_id", nullable = false)
    private UUID meetingId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "origin_label")
    private String originLabel;

    @Column(name = "origin_lat")
    private Double originLat;

    @Column(name = "origin_lng")
    private Double originLng;

    @Column(name = "prep_minutes")
    private Integer prepMinutes;

    @Column(name = "opted_in", nullable = false)
    private boolean optedIn;

    @Column(name = "join_order", nullable = false)
    private int joinOrder;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected Participant() {
    }

    static Participant join(UUID meetingId, String tokenHash, String nickname, Role role,
                            OriginInfo origin, int joinOrder, Instant now) {
        Participant p = new Participant();
        p.id = UUID.randomUUID();
        p.meetingId = meetingId;
        p.tokenHash = tokenHash;
        p.nickname = nickname;
        p.role = role;
        p.joinOrder = joinOrder;
        p.joinedAt = now;
        if (origin != null) {
            p.changeOrigin(origin);
        }
        return p;
    }

    void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    void changeOrigin(OriginInfo origin) {
        this.originLabel = origin.label();
        this.originLat = origin.lat();
        this.originLng = origin.lng();
    }

    void changePrepMinutes(int prepMinutes) {
        this.prepMinutes = prepMinutes;
    }

    void changeOptedIn(boolean optedIn) {
        this.optedIn = optedIn;
    }

    void promoteToHost() {
        this.role = Role.HOST;
    }

    OriginInfo origin() {
        return originLabel == null ? null : new OriginInfo(originLabel, originLat, originLng);
    }

    public UUID id() {
        return id;
    }

    public UUID meetingId() {
        return meetingId;
    }

    public Role role() {
        return role;
    }

    String nickname() {
        return nickname;
    }

    Integer prepMinutes() {
        return prepMinutes;
    }

    boolean optedIn() {
        return optedIn;
    }

    int joinOrder() {
        return joinOrder;
    }

    /** 출발지. label은 사용자가 고른 장소 이름. */
    public record OriginInfo(String label, double lat, double lng) {
    }
}
