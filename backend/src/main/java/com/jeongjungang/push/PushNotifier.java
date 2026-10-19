package com.jeongjungang.push;

import com.jeongjungang.common.AfterCommit;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * 책임 알람 푸시 (docs/API.md 8절). 받을 기기는 지금(트랜잭션 안에서) 찾고, 보내는 건 커밋 뒤에 한다.
 * 참가자가 나갈 때처럼 기기 행이 곧 지워지는 경우에도 취소 푸시를 보낼 수 있게 하기 위함이다.
 * 내용은 최소로 담는다. 앱은 받으면 GET으로 자세한 내용을 가져온다.
 */
@Component
public class PushNotifier {

    static final String TYPE = "type";
    static final String ALARM_SYNC = "ALARM_SYNC";
    static final String ALARM_CANCELLED = "ALARM_CANCELLED";
    static final String ESCALATION = "ESCALATION";
    /** 오래 지난 뒤 도착한 "아직 안 일어남"은 의미가 없다. */
    static final Duration ESCALATION_TTL = Duration.ofSeconds(120);

    private final DeviceRepository devices;
    private final PushSender sender;

    PushNotifier(DeviceRepository devices, PushSender sender) {
        this.devices = devices;
        this.sender = sender;
    }

    /** 알람이 만들어지거나 시각이 바뀜 → 해당 참가자. */
    public void alarmSync(UUID meetingId, Collection<UUID> participantIds) {
        sendAfterCommit(participantIds, token -> new PushMessage(token,
                Map.of(TYPE, ALARM_SYNC, "meetingId", meetingId.toString()), null));
    }

    /** 약속 취소, 알람 끔, 참가자 탈퇴 → 해당 참가자. */
    public void alarmCancelled(UUID meetingId, Collection<UUID> participantIds) {
        sendAfterCommit(participantIds, token -> new PushMessage(token,
                Map.of(TYPE, ALARM_CANCELLED, "meetingId", meetingId.toString()), null));
    }

    /** 유예시간 안에 못 끔 → 같은 약속의 나머지 멤버. */
    public void escalation(Escalation e, Collection<UUID> recipientIds) {
        Map<String, String> data = Map.of(
                TYPE, ESCALATION,
                "meetingId", e.meetingId().toString(),
                "alarmId", e.alarmId().toString(),
                "sleeperNickname", e.sleeperNickname(),
                "fireAt", e.fireAt().toString(),
                "escalatedAt", e.escalatedAt().toString());
        sendAfterCommit(recipientIds, token -> new PushMessage(token, data, ESCALATION_TTL));
    }

    private void sendAfterCommit(Collection<UUID> participantIds, Function<String, PushMessage> toMessage) {
        if (participantIds.isEmpty()) {
            return;
        }
        List<PushMessage> messages = devices.findByParticipantIdIn(participantIds).stream()
                .map(Device::fcmToken)
                .map(toMessage)
                .toList();
        if (!messages.isEmpty()) {
            AfterCommit.run("푸시 보내기", () -> sender.send(messages));
        }
    }

    public record Escalation(UUID meetingId, UUID alarmId, String sleeperNickname, Instant fireAt,
                             Instant escalatedAt) {
    }
}
