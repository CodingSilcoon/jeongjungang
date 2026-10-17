package com.jeongjungang.alarm;

import com.jeongjungang.alarm.api.AlarmRequests.EnableAccountability;
import com.jeongjungang.alarm.api.AlarmRequests.ReportTravel;
import com.jeongjungang.auth.Caller;
import com.jeongjungang.common.ApiException;
import com.jeongjungang.common.ErrorCode;
import com.jeongjungang.meeting.Meeting;
import com.jeongjungang.meeting.MeetingService;
import com.jeongjungang.meeting.Participant;
import com.jeongjungang.meeting.ParticipantRepository;
import com.jeongjungang.meeting.ParticipantService;
import com.jeongjungang.meeting.api.MeetingResponses.AccountabilityView;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 책임 알람 켜기·끄기, 이동시간 보고 (docs/API.md 7절). 모두 약속 행을 잠그고 한다. */
@Service
public class AccountabilityService {

    private final MeetingService meetingService;
    private final ParticipantService participantService;
    private final ParticipantRepository participants;
    private final AlarmPlanner planner;

    AccountabilityService(MeetingService meetingService, ParticipantService participantService,
                          ParticipantRepository participants, AlarmPlanner planner) {
        this.meetingService = meetingService;
        this.participantService = participantService;
        this.participants = participants;
        this.planner = planner;
    }

    /** 방장만. 전원 동의, 장소·시간 확정이 필요하다. 이미 켜져 있으면 설정만 바꾼다. */
    @Transactional
    public AccountabilityView enable(Caller caller, UUID meetingId, EnableAccountability request) {
        Meeting meeting = meetingService.lockForHost(caller, meetingId);
        boolean allOptedIn = participants.findByMeetingIdOrderByJoinOrderAsc(meetingId).stream()
                .allMatch(Participant::optedIn);
        if (!allOptedIn) {
            throw new ApiException(ErrorCode.NOT_ALL_OPTED_IN);
        }
        if (meeting.place() == null || meeting.meetAt() == null) {
            throw new ApiException(ErrorCode.ALARM_NOT_READY);
        }
        // 보내지 않은 값은 지금 설정을 유지한다(처음에는 기본값 60초·5분)
        int grace = request.gracePeriodSec() != null ? request.gracePeriodSec() : meeting.gracePeriodSec();
        int margin = request.marginMinutes() != null ? request.marginMinutes() : meeting.marginMinutes();
        meeting.enableAccountability(grace, margin);
        planner.reconcile(meeting);
        return view(meeting);
    }

    /** 방장만. 모든 알람이 CANCELLED가 된다. */
    @Transactional
    public void disable(Caller caller, UUID meetingId) {
        Meeting meeting = meetingService.lockForHost(caller, meetingId);
        if (meeting.accountabilityEnabled()) {
            meeting.disableAccountability();
        }
        planner.reconcile(meeting);
    }

    /** 본인만. 확정 장소 이름이 다르면(장소가 바뀌었는데 예전 값을 보냄) ALARM_NOT_READY. */
    @Transactional
    public void reportTravel(Caller caller, UUID participantId, ReportTravel request) {
        Meeting meeting = meetingService.lockOwnMeeting(caller, caller.meetingId());
        Participant me = participantService.findOwn(caller, meeting, participantId);
        String placeName = request.placeName().strip();
        if (meeting.place() == null || !meeting.place().name().equals(placeName)) {
            throw new ApiException(ErrorCode.ALARM_NOT_READY);
        }
        me.reportTravel(placeName, request.minutes(), request.transfers());
        planner.reconcile(meeting);
    }

    static AccountabilityView view(Meeting meeting) {
        return new AccountabilityView(meeting.accountabilityEnabled(), meeting.gracePeriodSec(),
                meeting.marginMinutes());
    }
}
