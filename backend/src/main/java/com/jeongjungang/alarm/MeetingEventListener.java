package com.jeongjungang.alarm;

import com.jeongjungang.meeting.MeetingEvents.AlarmInputsChanged;
import com.jeongjungang.meeting.MeetingEvents.MeetingCancelling;
import com.jeongjungang.meeting.MeetingEvents.ParticipantLeaving;
import com.jeongjungang.meeting.MeetingRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 약속 쪽 변화에 알람을 맞춘다. 이벤트를 보낸 트랜잭션 안에서 동기로 돈다(약속 행이 이미 잠겨 있다).
 * 여기서 예외가 나면 원래 요청도 롤백된다.
 */
@Component
class MeetingEventListener {

    private final AlarmPlanner planner;
    private final MeetingRepository meetings;

    MeetingEventListener(AlarmPlanner planner, MeetingRepository meetings) {
        this.planner = planner;
        this.meetings = meetings;
    }

    @EventListener
    void on(AlarmInputsChanged event) {
        meetings.findById(event.meetingId()).ifPresent(planner::reconcile);
    }

    @EventListener
    void on(ParticipantLeaving event) {
        planner.participantLeaving(event.meetingId(), event.participantId());
    }

    @EventListener
    void on(MeetingCancelling event) {
        planner.meetingCancelling(event.meetingId());
    }
}
