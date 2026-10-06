package com.jeongjungang.meeting;

import com.jeongjungang.auth.Caller;
import com.jeongjungang.common.ApiException;
import com.jeongjungang.common.ErrorCode;
import com.jeongjungang.meeting.api.MeetingRequests.UpdateParticipant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 참가자 정보 수정, 나가기, 내보내기 (docs/API.md 6절). 모두 약속 행을 잠그고 한다. */
@Service
public class ParticipantService {

    private final MeetingRepository meetings;
    private final ParticipantRepository participants;
    private final MeetingService meetingService;

    ParticipantService(MeetingRepository meetings, ParticipantRepository participants, MeetingService meetingService) {
        this.meetings = meetings;
        this.participants = participants;
        this.meetingService = meetingService;
    }

    /** 본인 정보만 바꿀 수 있다. 보낸 필드만 바뀐다. */
    @Transactional
    public void update(Caller caller, UUID participantId, UpdateParticipant request) {
        Meeting meeting = lockCallerMeeting(caller);
        Participant target = findInMeeting(meeting, participantId);
        if (!target.id().equals(caller.participantId())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        if (request.nickname() != null) {
            target.changeNickname(request.nickname().strip());
        }
        if (request.origin() != null) {
            target.changeOrigin(request.origin().toInfo());
        }
        if (request.prepMinutes() != null) {
            target.changePrepMinutes(request.prepMinutes());
        }
        if (request.optedIn() != null) {
            target.changeOptedIn(request.optedIn());
        }
        meeting.touch();
    }

    /**
     * 본인이면 나가기, 방장이 남을 지우면 내보내기.
     * 방장이 나가면 가장 먼저 들어온 사람이 방장이 되고, 혼자였으면 약속을 취소한다(2026-10-02 결정).
     */
    @Transactional
    public void remove(Caller caller, UUID participantId) {
        Meeting meeting = lockCallerMeeting(caller);
        Participant target = findInMeeting(meeting, participantId);
        boolean leavingSelf = target.id().equals(caller.participantId());
        if (!leavingSelf && !meetingService.isHostNow(caller)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }

        if (leavingSelf && target.role() == Role.HOST) {
            List<Participant> others = participants.findByMeetingIdOrderByJoinOrderAsc(meeting.id()).stream()
                    .filter(p -> !p.id().equals(target.id()))
                    .toList();
            if (others.isEmpty()) {
                meetings.delete(meeting);
                return;
            }
            others.get(0).promoteToHost();
        }
        participants.delete(target);
        meeting.touch();
    }

    private Meeting lockCallerMeeting(Caller caller) {
        Meeting meeting = meetings.findByIdForUpdate(caller.meetingId())
                .orElseThrow(() -> new ApiException(ErrorCode.MEETING_NOT_FOUND));
        meetingService.requireNotExpired(meeting);
        return meeting;
    }

    /** 다른 약속의 참가자는 있는지조차 알려 주지 않는다. */
    private Participant findInMeeting(Meeting meeting, UUID participantId) {
        return participants.findById(participantId)
                .filter(p -> p.meetingId().equals(meeting.id()))
                .orElseThrow(() -> new ApiException(ErrorCode.PARTICIPANT_NOT_FOUND));
    }
}
