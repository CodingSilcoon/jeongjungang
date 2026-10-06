package com.jeongjungang.meeting.api;

import com.jeongjungang.meeting.MeetingStatus;
import com.jeongjungang.meeting.Purpose;
import com.jeongjungang.meeting.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 약속·참가자 응답 본문 (docs/API.md 6절). 시각은 UTC ISO 8601로 나간다. */
public final class MeetingResponses {

    private MeetingResponses() {
    }

    public record MeetingView(
            UUID id,
            String inviteCode,
            String inviteUrl,
            String title,
            Purpose purpose,
            Instant meetAt,
            MeetingStatus status,
            PlaceView place,
            Instant expiresAt,
            AccountabilityView accountability) {
    }

    public record PlaceView(String name, double lat, double lng, List<Integer> lines) {
    }

    public record AccountabilityView(boolean enabled, int gracePeriodSec, int marginMinutes) {
    }

    public record OriginView(String label, double lat, double lng) {
    }

    /** 참가·생성 응답의 짧은 참가자 정보. */
    public record ParticipantSummary(UUID id, String nickname, Role role) {
    }

    public record ParticipantView(UUID id, String nickname, Role role, OriginView origin,
                                  Integer prepMinutes, boolean optedIn) {
    }

    public record Me(UUID participantId, Role role) {
    }

    public record Created(MeetingView meeting, ParticipantSummary participant, String participantToken) {
    }

    public record Joined(ParticipantSummary participant, String participantToken, UUID meetingId) {
    }

    public record InvitePreview(String title, String hostNickname, long participantCount,
                                MeetingStatus status, Instant meetAt) {
    }

    public record Snapshot(long version, MeetingView meeting, List<ParticipantView> participants,
                           Me me, Instant serverTime) {
    }
}
