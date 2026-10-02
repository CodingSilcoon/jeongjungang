package com.jeongjungang.data.remote.meeting;

import com.jeongjungang.data.remote.ApiException;

/**
 * 책임 알람 API (docs/API.md 7절). 모두 네트워크 호출이라 메인 스레드에서 부르지 않는다.
 * 준비시간·동의는 {@link ParticipantUpdate}로 바꾼다.
 */
public interface AccountabilityApi {

    /** `dismiss`의 source. */
    enum DismissSource { DEVICE, MANUAL_AWAKE }

    /** 서버 알람 상태. 모르는 값은 UNKNOWN. */
    enum AlarmStatus {
        SCHEDULED, RINGING, DISMISSED, ESCALATED, CANCELLED, UNKNOWN;

        static AlarmStatus parse(String s) {
            for (AlarmStatus a : values()) {
                if (a.name().equals(s)) {
                    return a;
                }
            }
            return UNKNOWN;
        }

        /** 기기에서 울려야 하는 상태. */
        public boolean shouldRing() {
            return this == SCHEDULED || this == RINGING;
        }
    }

    /** `GET /meetings/{id}/alarms` 결과. */
    final class MyAlarm {
        public final boolean enabled;
        public final int gracePeriodSec;
        /** 알람이 아직 없으면 null. */
        public final String alarmId;
        public final long fireAtMillis;
        public final long graceUntilMillis;
        public final AlarmStatus status;
        /** 응답의 서버 시각. 기기 시계 보정용. 없으면 0. */
        public final long serverTimeMillis;

        MyAlarm(boolean enabled, int gracePeriodSec, String alarmId, long fireAtMillis, long graceUntilMillis,
                AlarmStatus status, long serverTimeMillis) {
            this.enabled = enabled;
            this.gracePeriodSec = gracePeriodSec;
            this.alarmId = alarmId;
            this.fireAtMillis = fireAtMillis;
            this.graceUntilMillis = graceUntilMillis;
            this.status = status;
            this.serverTimeMillis = serverTimeMillis;
        }
    }

    /** `PUT /participants/{id}/travel`. placeName은 서버의 확정 장소 이름과 같아야 한다. */
    void reportTravel(String participantId, String token, String placeName, double minutes, int transfers)
            throws ApiException;

    /** `POST /meetings/{id}/accountability`. 방장만, 전원 동의일 때만. */
    void enable(String meetingId, String token, int gracePeriodSec, int marginMinutes) throws ApiException;

    /** `DELETE /meetings/{id}/accountability`. 방장만. */
    void disable(String meetingId, String token) throws ApiException;

    /** `POST /devices`. 약속마다 내 토큰으로 등록한다. */
    void registerDevice(String token, String fcmToken, String appVersion) throws ApiException;

    /** `GET /meetings/{id}/alarms`. */
    MyAlarm myAlarm(String meetingId, String token) throws ApiException;

    /** `POST /alarms/{id}/ringing`. 선택(상태 표시용). */
    void ringing(String alarmId, String token) throws ApiException;

    /** `POST /alarms/{id}/dismiss`. 멱등. @return 처리 후 서버 상태(이미 ESCALATED면 ESCALATED) */
    AlarmStatus dismiss(String alarmId, String token, long dismissedAtMillis, DismissSource source)
            throws ApiException;
}
