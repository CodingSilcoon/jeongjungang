package com.jeongjungang.meeting.api;

import com.jeongjungang.meeting.MeetingStatus;
import com.jeongjungang.meeting.Purpose;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** 약속·참가자 요청 본문 (docs/API.md 6절). 수정 요청에서 null은 "보내지 않음"이라 바꾸지 않는다. */
public final class MeetingRequests {

    private MeetingRequests() {
    }

    public record CreateMeeting(
            @Size(max = 40) String title,
            @NotNull @Nickname String hostNickname,
            Purpose purpose,
            OffsetDateTime meetAt,
            @Valid OriginRequest origin) {
    }

    public record JoinMeeting(
            @NotNull @Nickname String nickname,
            @Valid OriginRequest origin) {
    }

    public record UpdateMeeting(
            @Size(max = 40) String title,
            OffsetDateTime meetAt,
            Purpose purpose,
            @Valid PlaceRequest place,
            MeetingStatus status) {
    }

    public record UpdateParticipant(
            @Nickname String nickname,
            @Valid OriginRequest origin,
            @Min(0) @Max(240) Integer prepMinutes,
            Boolean optedIn) {
    }
}
