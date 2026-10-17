package com.jeongjungang.alarm;

import com.jeongjungang.alarm.api.AlarmRequests.DismissAlarm;
import com.jeongjungang.alarm.api.AlarmResponses.AlarmView;
import com.jeongjungang.alarm.api.AlarmResponses.DismissResult;
import com.jeongjungang.alarm.api.AlarmResponses.MyAlarm;
import com.jeongjungang.auth.Caller;
import com.jeongjungang.common.ApiException;
import com.jeongjungang.common.ErrorCode;
import com.jeongjungang.meeting.Meeting;
import com.jeongjungang.meeting.MeetingService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 알람 조회, 울림·끔 보고 (docs/API.md 7절). 남의 알람은 있는지조차 알려 주지 않는다. */
@Service
public class AlarmService {

    private final AlarmRepository alarms;
    private final MeetingService meetingService;
    private final EscalationService escalation;
    private final Clock clock;

    AlarmService(AlarmRepository alarms, MeetingService meetingService, EscalationService escalation, Clock clock) {
        this.alarms = alarms;
        this.meetingService = meetingService;
        this.escalation = escalation;
        this.clock = clock;
    }

    /** 푸시를 놓쳤을 때 동기화용. 내 알람만, 없으면 alarm은 null. */
    @Transactional(readOnly = true)
    public MyAlarm myAlarm(Caller caller, UUID meetingId) {
        Meeting meeting = meetingService.findOwnMeeting(caller, meetingId);
        AlarmView alarm = alarms.findByParticipantId(caller.participantId())
                .map(a -> new AlarmView(a.id(), a.fireAt(), a.graceUntil(), a.status()))
                .orElse(null);
        return new MyAlarm(AccountabilityService.view(meeting), alarm, clock.instant());
    }

    /** 상태 표시용. 보내지 않아도 에스컬레이션은 동작한다. */
    @Transactional
    public void ringing(Caller caller, UUID alarmId) {
        lockOwn(caller, alarmId).markRinging(clock.instant());
    }

    /**
     * 멱등. 같은 요청을 몇 번 보내도 결과가 같고, 이미 ESCALATED면 되돌리지 않는다.
     * 유예시간이 지난 뒤에 온 보고는 끔으로 치지 않고, 아직 에스컬레이션 전이면 지금 에스컬레이션한다.
     */
    @Transactional
    public DismissResult dismiss(Caller caller, UUID alarmId, DismissAlarm request) {
        Alarm alarm = lockOwn(caller, alarmId);
        Instant now = clock.instant();
        if (escalation.escalateLocked(alarm, now)) {
            return new DismissResult(alarm.status());
        }
        return new DismissResult(alarm.dismiss(request.sourceOrDefault(), now));
    }

    private Alarm lockOwn(Caller caller, UUID alarmId) {
        return alarms.findByIdForUpdate(alarmId)
                .filter(a -> a.participantId().equals(caller.participantId()))
                .orElseThrow(() -> new ApiException(ErrorCode.ALARM_NOT_FOUND));
    }
}
