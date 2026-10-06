package com.jeongjungang.meeting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 약속. 상태를 바꾸는 메서드는 모두 stateVersion을 올린다(폴링 ETag 기준). */
@Entity
@Table(name = "meetings")
public class Meeting {

    static final Duration TTL_AFTER_MEET = Duration.ofHours(24);
    static final Duration TTL_WITHOUT_MEET = Duration.ofDays(7);
    static final int DEFAULT_GRACE_PERIOD_SEC = 60;
    static final int DEFAULT_MARGIN_MINUTES = 5;

    @Id
    private UUID id;

    @Column(name = "invite_code", nullable = false, unique = true, length = 8)
    private String inviteCode;

    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Purpose purpose;

    @Column(name = "meet_at")
    private Instant meetAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MeetingStatus status;

    @Column(name = "place_name")
    private String placeName;

    @Column(name = "place_lat")
    private Double placeLat;

    @Column(name = "place_lng")
    private Double placeLng;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "place_lines")
    private Integer[] placeLines;

    @Column(name = "accountability_enabled", nullable = false)
    private boolean accountabilityEnabled;

    @Column(name = "grace_period_sec", nullable = false)
    private int gracePeriodSec;

    @Column(name = "margin_minutes", nullable = false)
    private int marginMinutes;

    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected Meeting() {
    }

    static Meeting create(String inviteCode, String title, Purpose purpose, Instant meetAt, Instant now) {
        Meeting m = new Meeting();
        m.id = UUID.randomUUID();
        m.inviteCode = inviteCode;
        m.title = title;
        m.purpose = purpose == null ? Purpose.ETC : purpose;
        m.meetAt = meetAt;
        m.status = MeetingStatus.OPEN;
        m.gracePeriodSec = DEFAULT_GRACE_PERIOD_SEC;
        m.marginMinutes = DEFAULT_MARGIN_MINUTES;
        m.stateVersion = 1;
        m.createdAt = now;
        m.expiresAt = expiryFor(meetAt, now);
        return m;
    }

    /** 약속 시각 + 24시간, 약속 시각이 없으면 생성 + 7일 (docs/API.md 4절). */
    static Instant expiryFor(Instant meetAt, Instant createdAt) {
        return meetAt != null ? meetAt.plus(TTL_AFTER_MEET) : createdAt.plus(TTL_WITHOUT_MEET);
    }

    boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    void changeTitle(String title) {
        this.title = title;
        touch();
    }

    void changePurpose(Purpose purpose) {
        this.purpose = purpose;
        touch();
    }

    void changeMeetAt(Instant meetAt) {
        this.meetAt = meetAt;
        this.expiresAt = expiryFor(meetAt, createdAt);
        touch();
    }

    void changePlace(PlaceInfo place) {
        this.placeName = place.name();
        this.placeLat = place.lat();
        this.placeLng = place.lng();
        this.placeLines = place.lines().toArray(Integer[]::new);
        touch();
    }

    void changeStatus(MeetingStatus status) {
        this.status = status;
        touch();
    }

    /** 참가자 정보처럼 약속 밖의 변화도 폴링에 보이도록 버전만 올린다. */
    void touch() {
        stateVersion++;
    }

    PlaceInfo place() {
        if (placeName == null) {
            return null;
        }
        List<Integer> lines = placeLines == null ? List.of() : List.of(placeLines);
        return new PlaceInfo(placeName, placeLat, placeLng, lines);
    }

    public UUID id() {
        return id;
    }

    String inviteCode() {
        return inviteCode;
    }

    String title() {
        return title;
    }

    Purpose purpose() {
        return purpose;
    }

    Instant meetAt() {
        return meetAt;
    }

    MeetingStatus status() {
        return status;
    }

    boolean accountabilityEnabled() {
        return accountabilityEnabled;
    }

    int gracePeriodSec() {
        return gracePeriodSec;
    }

    int marginMinutes() {
        return marginMinutes;
    }

    long stateVersion() {
        return stateVersion;
    }

    Instant expiresAt() {
        return expiresAt;
    }

    /** 확정 장소. lines는 지나는 호선 번호. */
    public record PlaceInfo(String name, double lat, double lng, List<Integer> lines) {
        public PlaceInfo {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }
}
