package com.jeongjungang.alarm;

import com.jeongjungang.common.AfterCommit;
import com.jeongjungang.meeting.Participant;
import com.jeongjungang.meeting.ParticipantRepository;
import com.jeongjungang.push.PushNotifier;
import com.jeongjungang.push.PushNotifier.Escalation;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유예시간이 지난 알람을 확인한다. 기기 보고와 관계없이 서버 시각으로 판단한다(폰이 꺼져 있어도 에스컬레이션돼야 한다).
 * 알람 행을 잠그고 상태를 다시 확인한 뒤 바꾸므로, 끔 보고와 동시에 와도 한쪽만 이긴다. 알람당 한 번만 보낸다.
 */
@Service
public class EscalationService {

    private static final Logger log = LoggerFactory.getLogger(EscalationService.class);

    private final AlarmRepository alarms;
    private final ParticipantRepository participants;
    private final EscalationQueue queue;
    private final PushNotifier push;
    private final Clock clock;

    EscalationService(AlarmRepository alarms, ParticipantRepository participants, EscalationQueue queue,
                      PushNotifier push, Clock clock) {
        this.alarms = alarms;
        this.participants = participants;
        this.queue = queue;
        this.push = push;
        this.clock = clock;
    }

    /** 큐에서 꺼낸 알람 하나를 처리한다. 에스컬레이션했으면 true. */
    @Transactional
    public boolean escalate(UUID alarmId) {
        Alarm alarm = alarms.findByIdForUpdate(alarmId).orElse(null);
        if (alarm == null) {
            return false; // 약속이 지워졌거나 참가자가 나갔다
        }
        if (escalateLocked(alarm, clock.instant())) {
            return true;
        }
        if (alarm.status().isActive()) {
            // 아직 기한 전이다(큐에 이른 기한이 남아 있었거나 시각이 뒤로 바뀜). DB 기한으로 다시 넣는다
            AfterCommit.run("에스컬레이션 큐 다시 넣기", () -> queue.add(alarm.id(), alarm.graceUntil()));
        }
        return false;
    }

    /**
     * 잠근 알람의 기한이 지났으면 ESCALATED로 바꾸고 나머지 멤버에게 푸시한다. 끔 보고가 기한 뒤에 왔을 때도 쓴다
     * (주기 작업이 늦어졌다고 기한 지난 끔 보고가 에스컬레이션을 이기면 안 된다).
     */
    boolean escalateLocked(Alarm alarm, Instant now) {
        if (!alarm.escalateIfDue(now)) {
            return false;
        }
        List<Participant> members = participants.findByMeetingIdOrderByJoinOrderAsc(alarm.meetingId());
        String sleeper = members.stream()
                .filter(p -> p.id().equals(alarm.participantId()))
                .map(Participant::nickname)
                .findFirst()
                .orElse("");
        // 동의한 나머지 멤버만. 동의 안 한 사람 폰은 울리면 안 된다
        List<UUID> recipients = members.stream()
                .filter(p -> p.optedIn() && !p.id().equals(alarm.participantId()))
                .map(Participant::id)
                .toList();
        push.escalation(new Escalation(alarm.meetingId(), alarm.id(), sleeper, alarm.fireAt(), now), recipients);
        log.info("알람 {} 에스컬레이션, 받는 사람 {}명", alarm.id(), recipients.size());
        return true;
    }

    /**
     * DB의 대기 중 알람을 큐에 다시 넣는다. Redis가 재시작돼 큐가 비었거나, 커밋 뒤 큐 갱신이 실패했거나,
     * 큐에서 꺼낸 직후 서버가 죽은 경우를 메운다. 이미 있는 항목은 더 이른 기한을 남긴다.
     */
    @Transactional(readOnly = true)
    public int reseedQueue() {
        List<DueAlarm> active = alarms.findActive();
        queue.addAll(active);
        return active.size();
    }
}
