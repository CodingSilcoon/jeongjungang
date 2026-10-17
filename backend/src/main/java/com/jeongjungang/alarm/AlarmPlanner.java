package com.jeongjungang.alarm;

import com.jeongjungang.common.AfterCommit;
import com.jeongjungang.meeting.Meeting;
import com.jeongjungang.meeting.Meeting.PlaceInfo;
import com.jeongjungang.meeting.Participant;
import com.jeongjungang.meeting.ParticipantRepository;
import com.jeongjungang.push.PushNotifier;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 약속의 지금 상태에 맞게 참가자별 알람을 만들고, 다시 걸고, 취소한다.
 * 약속 행을 잠근 트랜잭션 안에서 부른다. 푸시와 에스컬레이션 큐 변경은 커밋 뒤에 한다.
 *
 * <p>알람이 있어야 하는 참가자: 책임 알람이 켜졌고, 장소·시간이 정해졌고, 본인이 동의했고,
 * 준비시간과 지금 장소 기준 이동시간을 보고한 사람.
 * 알람시각이 이미 지났으면 새로 만들지 않는다(만들면 곧바로 에스컬레이션되기 때문).
 */
@Component
class AlarmPlanner {

    private enum Outcome { UNCHANGED, SYNCED, CANCELLED }

    private final AlarmRepository alarms;
    private final ParticipantRepository participants;
    private final EscalationQueue queue;
    private final PushNotifier push;
    private final Clock clock;

    AlarmPlanner(AlarmRepository alarms, ParticipantRepository participants, EscalationQueue queue,
                 PushNotifier push, Clock clock) {
        this.alarms = alarms;
        this.participants = participants;
        this.queue = queue;
        this.push = push;
        this.clock = clock;
    }

    void reconcile(Meeting meeting) {
        Instant now = clock.instant();
        Map<UUID, Alarm> existing = alarms.findByMeetingIdForUpdate(meeting.id()).stream()
                .collect(Collectors.toMap(Alarm::participantId, Function.identity()));
        List<Alarm> synced = new ArrayList<>();
        List<Alarm> cancelled = new ArrayList<>();

        for (Participant p : participants.findByMeetingIdOrderByJoinOrderAsc(meeting.id())) {
            Optional<AlarmTimes> desired = desiredTimes(meeting, p);
            Alarm alarm = existing.get(p.id());
            if (alarm == null) {
                if (desired.isPresent() && !desired.get().fireAt().isBefore(now)) {
                    synced.add(alarms.save(Alarm.schedule(meeting.id(), p.id(), desired.get(), now)));
                }
                continue;
            }
            Outcome outcome = desired.isPresent() ? update(alarm, desired.get(), now) : cancelIfLive(alarm, now);
            if (outcome == Outcome.SYNCED) {
                synced.add(alarm);
            } else if (outcome == Outcome.CANCELLED) {
                cancelled.add(alarm);
            }
        }

        if (synced.isEmpty() && cancelled.isEmpty()) {
            return;
        }
        // 앱은 약속 폴링 때 알람도 맞추므로, 알람이 바뀌면 304가 나오지 않게 버전을 올린다
        meeting.touch();
        push.alarmSync(meeting.id(), participantIds(synced));
        push.alarmCancelled(meeting.id(), participantIds(cancelled));
        // 취소된 알람은 큐에서 빼지 않는다. 꺼냈을 때 DB 상태를 보고 버린다(EscalationQueue#add 참고)
        List<DueAlarm> toQueue = synced.stream().map(a -> new DueAlarm(a.id(), a.graceUntil())).toList();
        if (!toQueue.isEmpty()) {
            AfterCommit.run("에스컬레이션 큐 갱신", () -> queue.addAll(toQueue));
        }
    }

    /** 참가자가 나가기 직전. 알람 행은 외래키로 지워지니 취소 푸시만 보낸다. */
    void participantLeaving(UUID meetingId, UUID participantId) {
        alarms.findByParticipantId(participantId).ifPresent(alarm -> notifyBeforeDelete(meetingId, List.of(alarm)));
    }

    /** 약속이 지워지기 직전. */
    void meetingCancelling(UUID meetingId) {
        notifyBeforeDelete(meetingId, alarms.findByMeetingIdForUpdate(meetingId));
    }

    private void notifyBeforeDelete(UUID meetingId, List<Alarm> doomed) {
        List<Alarm> active = doomed.stream().filter(a -> a.status().isActive()).toList();
        push.alarmCancelled(meetingId, participantIds(active));
    }

    private static Optional<AlarmTimes> desiredTimes(Meeting meeting, Participant p) {
        PlaceInfo place = meeting.place();
        if (!meeting.accountabilityEnabled() || meeting.meetAt() == null || place == null
                || !p.optedIn() || p.prepMinutes() == null) {
            return Optional.empty();
        }
        Double travel = p.travelMinutesTo(place.name());
        if (travel == null) {
            return Optional.empty();
        }
        return Optional.of(AlarmTimes.of(meeting.meetAt(), travel, p.prepMinutes(), meeting.marginMinutes(),
                meeting.gracePeriodSec()));
    }

    /** 같은 시각이면 그대로(끝난 알람도 되살리지 않는다), 시각이 바뀌었거나 취소됐던 알람이면 다시 건다. */
    private static Outcome update(Alarm alarm, AlarmTimes desired, Instant now) {
        if (alarm.hasTimes(desired) && alarm.status() != AlarmStatus.CANCELLED) {
            return Outcome.UNCHANGED;
        }
        if (desired.fireAt().isBefore(now)) {
            // 새 시각이 이미 지났다. 울릴 수 없으니 기다리던 알람만 취소하고, 끝난 알람은 그대로 둔다
            return alarm.status().isActive() ? cancel(alarm, now) : Outcome.UNCHANGED;
        }
        alarm.reschedule(desired, now);
        return Outcome.SYNCED;
    }

    /**
     * 기다리던 알람만 취소한다. 끝난 알람(DISMISSED·ESCALATED)은 기록을 남긴다.
     * 끝난 알람까지 CANCELLED로 바꾸면, 동의를 껐다 켜는 것만으로 이미 끈 알람이 다시 걸린다.
     */
    private static Outcome cancelIfLive(Alarm alarm, Instant now) {
        if (!alarm.status().isActive()) {
            return Outcome.UNCHANGED;
        }
        return cancel(alarm, now);
    }

    private static Outcome cancel(Alarm alarm, Instant now) {
        alarm.cancel(now);
        return Outcome.CANCELLED;
    }

    private static List<UUID> participantIds(List<Alarm> list) {
        return list.stream().map(Alarm::participantId).toList();
    }
}
