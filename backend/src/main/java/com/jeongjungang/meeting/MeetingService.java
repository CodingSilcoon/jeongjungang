package com.jeongjungang.meeting;

import com.jeongjungang.auth.Caller;
import com.jeongjungang.auth.ParticipantTokens;
import com.jeongjungang.common.ApiException;
import com.jeongjungang.common.ErrorCode;
import com.jeongjungang.meeting.Participant.OriginInfo;
import com.jeongjungang.meeting.api.MeetingRequests.CreateMeeting;
import com.jeongjungang.meeting.api.MeetingRequests.JoinMeeting;
import com.jeongjungang.meeting.api.MeetingRequests.UpdateMeeting;
import com.jeongjungang.meeting.api.MeetingResponses.Created;
import com.jeongjungang.meeting.api.MeetingResponses.InvitePreview;
import com.jeongjungang.meeting.api.MeetingResponses.Joined;
import com.jeongjungang.meeting.api.MeetingResponses.Me;
import com.jeongjungang.meeting.api.MeetingResponses.Snapshot;
import com.jeongjungang.meeting.api.OriginRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 약속 만들기·초대·조회·수정·취소 (docs/API.md 6절). */
@Service
public class MeetingService {

    public static final int MAX_PARTICIPANTS = 10;
    private static final int INVITE_CODE_ATTEMPTS = 5;
    /** 약속 시각은 1시간 전부터 1년 뒤까지만 받는다. 너무 먼 미래는 개인 정보 보관 기간(약속 + 24시간)을 늘린다. */
    static final Duration MEET_AT_PAST_LIMIT = Duration.ofHours(1);
    static final Duration MEET_AT_FUTURE_LIMIT = Duration.ofDays(365);

    private final MeetingRepository meetings;
    private final ParticipantRepository participants;
    private final MeetingViews views;
    private final Clock clock;

    MeetingService(MeetingRepository meetings, ParticipantRepository participants, MeetingViews views, Clock clock) {
        this.meetings = meetings;
        this.participants = participants;
        this.views = views;
        this.clock = clock;
    }

    @Transactional
    public Created create(CreateMeeting request) {
        Instant now = clock.instant();
        Meeting meeting = Meeting.create(newInviteCode(), normalizeTitle(request.title()), request.purpose(),
                validMeetAt(request.meetAt(), now), now);
        meetings.save(meeting);

        String token = ParticipantTokens.newToken();
        Participant host = Participant.join(meeting.id(), ParticipantTokens.hash(token),
                request.hostNickname().strip(), Role.HOST, originOf(request.origin()), 1, now);
        participants.save(host);
        return new Created(views.meeting(meeting), MeetingViews.summary(host), token);
    }

    @Transactional(readOnly = true)
    public InvitePreview preview(String rawCode) {
        Meeting meeting = InviteCodes.normalize(rawCode)
                .flatMap(meetings::findByInviteCode)
                .orElseThrow(() -> new ApiException(ErrorCode.MEETING_NOT_FOUND));
        requireNotExpired(meeting);
        List<Participant> members = participants.findByMeetingIdOrderByJoinOrderAsc(meeting.id());
        String hostNickname = members.stream()
                .filter(p -> p.role() == Role.HOST)
                .map(Participant::nickname)
                .findFirst()
                .orElse(null);
        return new InvitePreview(meeting.title(), hostNickname, members.size(), meeting.status(), meeting.meetAt());
    }

    @Transactional
    public Joined join(String rawCode, JoinMeeting request) {
        Meeting meeting = InviteCodes.normalize(rawCode)
                .flatMap(meetings::findByInviteCodeForUpdate)
                .orElseThrow(() -> new ApiException(ErrorCode.MEETING_NOT_FOUND));
        requireNotExpired(meeting);
        if (meeting.status() != MeetingStatus.OPEN) {
            throw new ApiException(ErrorCode.MEETING_CLOSED);
        }
        if (participants.countByMeetingId(meeting.id()) >= MAX_PARTICIPANTS) {
            throw new ApiException(ErrorCode.MEETING_FULL);
        }

        String token = ParticipantTokens.newToken();
        Participant member = Participant.join(meeting.id(), ParticipantTokens.hash(token),
                request.nickname().strip(), Role.MEMBER, originOf(request.origin()),
                participants.maxJoinOrder(meeting.id()) + 1, clock.instant());
        participants.save(member);
        meeting.touch();
        return new Joined(MeetingViews.summary(member), token, meeting.id());
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot(Caller caller, UUID meetingId) {
        requireOwnMeeting(caller, meetingId);
        Meeting meeting = meetings.findById(meetingId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEETING_NOT_FOUND));
        requireNotExpired(meeting);
        List<Participant> members = participants.findByMeetingIdOrderByJoinOrderAsc(meetingId);
        return new Snapshot(meeting.stateVersion(), views.meeting(meeting),
                members.stream().map(MeetingViews::participant).toList(),
                new Me(caller.participantId(), caller.role()), clock.instant());
    }

    @Transactional
    public void update(Caller caller, UUID meetingId, UpdateMeeting request) {
        Meeting meeting = lockForHost(caller, meetingId);
        if (request.title() != null) {
            meeting.changeTitle(normalizeTitle(request.title()));
        }
        if (request.purpose() != null) {
            meeting.changePurpose(request.purpose());
        }
        if (request.meetAt() != null) {
            meeting.changeMeetAt(validMeetAt(request.meetAt(), clock.instant()));
        }
        if (request.place() != null) {
            meeting.changePlace(request.place().toInfo());
        }
        if (request.status() != null) {
            if (request.status() == MeetingStatus.CONFIRMED) {
                requireConfirmable(meeting);
            }
            meeting.changeStatus(request.status());
        }
    }

    @Transactional
    public void cancel(Caller caller, UUID meetingId) {
        meetings.delete(lockForHost(caller, meetingId));
    }

    /** 방장만, 자기 약속만. 약속 행을 잠근 채로 돌려준다. */
    private Meeting lockForHost(Caller caller, UUID meetingId) {
        requireOwnMeeting(caller, meetingId);
        Meeting meeting = meetings.findByIdForUpdate(meetingId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEETING_NOT_FOUND));
        requireNotExpired(meeting);
        if (!isHostNow(caller)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return meeting;
    }

    /**
     * 약속 행을 잠근 뒤에 부른다. 토큰 확인 때 읽은 역할은 그사이 방장 넘기기로 바뀌었을 수 있어서 다시 읽는다.
     */
    boolean isHostNow(Caller caller) {
        return participants.findById(caller.participantId())
                .map(p -> p.role() == Role.HOST)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }

    static void requireOwnMeeting(Caller caller, UUID meetingId) {
        if (!caller.meetingId().equals(meetingId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
    }

    void requireNotExpired(Meeting meeting) {
        if (meeting.isExpired(clock.instant())) {
            throw new ApiException(ErrorCode.MEETING_EXPIRED);
        }
    }

    /** 확정에는 장소와 시간이 모두 있어야 한다. */
    private static void requireConfirmable(Meeting meeting) {
        Map<String, String> missing = new LinkedHashMap<>();
        if (meeting.place() == null) {
            missing.put("place", "확정하려면 장소가 필요합니다.");
        }
        if (meeting.meetAt() == null) {
            missing.put("meetAt", "확정하려면 약속 시간이 필요합니다.");
        }
        if (!missing.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, missing);
        }
    }

    private String newInviteCode() {
        for (int i = 0; i < INVITE_CODE_ATTEMPTS; i++) {
            String code = InviteCodes.random();
            if (!meetings.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("초대 코드를 만들지 못했습니다");
    }

    private static String normalizeTitle(String title) {
        if (title == null) {
            return null;
        }
        String trimmed = title.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Instant validMeetAt(OffsetDateTime time, Instant now) {
        if (time == null) {
            return null;
        }
        Instant meetAt = time.toInstant();
        if (meetAt.isBefore(now.minus(MEET_AT_PAST_LIMIT)) || meetAt.isAfter(now.plus(MEET_AT_FUTURE_LIMIT))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    Map.of("meetAt", "약속 시간은 지금부터 1년 안으로 정해 주세요."));
        }
        return meetAt;
    }

    private static OriginInfo originOf(OriginRequest origin) {
        return origin == null ? null : origin.toInfo();
    }
}
