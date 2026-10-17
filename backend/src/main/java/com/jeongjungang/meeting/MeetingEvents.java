package com.jeongjungang.meeting;

import java.util.UUID;

/**
 * 약속 쪽 변화를 책임 알람에 알리는 이벤트. 같은 트랜잭션 안에서 동기로 처리된다(약속 행을 잠근 채).
 * meeting 패키지가 alarm 패키지를 몰라도 되게 하려고 둔다.
 */
public final class MeetingEvents {

    private MeetingEvents() {
    }

    /** 알람시각에 쓰는 값(약속 시각, 장소, 준비시간, 동의)이 바뀜. */
    public record AlarmInputsChanged(UUID meetingId) {
    }

    /** 참가자가 지워지기 직전. 그 참가자의 알람·기기는 외래키로 함께 지워진다. */
    public record ParticipantLeaving(UUID meetingId, UUID participantId) {
    }

    /** 약속이 지워지기 직전(취소, 혼자 남은 방장이 나감). */
    public record MeetingCancelling(UUID meetingId) {
    }
}
