package com.jeongjungang.data.repository;

import android.content.Context;
import android.util.Log;
import com.jeongjungang.BuildConfig;
import com.jeongjungang.alarm.AlarmScheduler;
import com.jeongjungang.alarm.AlarmSpec;
import com.jeongjungang.alarm.DismissQueue;
import com.jeongjungang.data.remote.ApiException;
import com.jeongjungang.data.remote.meeting.AccountabilityApi;
import com.jeongjungang.data.remote.meeting.AccountabilityApi.AlarmStatus;
import com.jeongjungang.data.remote.meeting.AccountabilityApi.DismissSource;
import com.jeongjungang.data.remote.meeting.AccountabilityApi.MyAlarm;
import com.jeongjungang.data.remote.meeting.HttpAccountabilityApi;
import java.util.Map;

/**
 * 책임 알람의 서버 쪽(docs/API.md 7절)과 기기 알람({@link AlarmScheduler})을 잇는다.
 * 서버 시각이 기준이다: 서버 알람을 기기에 예약할 때 응답의 serverTime으로 기기 시계 차이를 보정한다.
 * 모든 메서드는 네트워크 호출이라 백그라운드에서 부른다.
 */
public final class AccountabilityRepository {

    /** {@link #syncAlarm} 결과. */
    public enum SyncResult {
        /** 기기에 예약됨(또는 이미 예약돼 있어 갱신됨). */
        SCHEDULED,
        /** 정확한 알람 권한이 없어 예약하지 못함. 화면에서 권한 설정으로 보낸다. */
        NEEDS_EXACT_ALARM_PERMISSION,
        /** 알람 시각이 이미 지남. */
        IN_PAST,
        /** 울릴 알람이 없음(책임 알람 꺼짐, 아직 안 만들어짐, 끔·취소·에스컬레이션됨). 기기 알람도 지웠다. */
        NO_ALARM
    }

    private static final String TAG = "AccountabilityRepo";
    private static volatile AccountabilityRepository instance;

    private final AccountabilityApi api;
    private final ParticipantTokenStore tokens;
    private final AlarmLinks links;
    private final AlarmScheduler scheduler;
    private final DismissQueue dismissals;

    AccountabilityRepository(AccountabilityApi api, ParticipantTokenStore tokens, AlarmLinks links,
                             AlarmScheduler scheduler, DismissQueue dismissals) {
        this.api = api;
        this.tokens = tokens;
        this.links = links;
        this.scheduler = scheduler;
        this.dismissals = dismissals;
    }

    public static AccountabilityRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (AccountabilityRepository.class) {
                if (instance == null) {
                    Context app = context.getApplicationContext();
                    AccountabilityApi api = BuildConfig.API_BASE_URL.isEmpty()
                            ? null : new HttpAccountabilityApi(BuildConfig.API_BASE_URL);
                    instance = new AccountabilityRepository(api, new ParticipantTokenStore(app),
                            new AlarmLinks(app), new AlarmScheduler(app), new DismissQueue(app));
                }
            }
        }
        return instance;
    }

    public boolean isAvailable() {
        return api != null;
    }

    /** 확정 장소까지 내 이동시간 보고. placeName은 서버의 확정 장소 이름 그대로. */
    public void reportTravel(String meetingId, String placeName, double minutes, int transfers) throws ApiException {
        ParticipantTokenStore.Entry me = member(meetingId);
        api().reportTravel(me.participantId, me.token, placeName, minutes, transfers);
    }

    /** 방장: 책임 알람 켜기. 전원 동의·장소 확정이 아니면 서버가 409로 거절한다. */
    public void enable(String meetingId, int gracePeriodSec, int marginMinutes) throws ApiException {
        api().enable(meetingId, member(meetingId).token, gracePeriodSec, marginMinutes);
    }

    /** 방장: 책임 알람 끄기. 내 기기 알람도 지운다(다른 사람 것은 서버 푸시로 지워진다). */
    public void disable(String meetingId) throws ApiException {
        api().disable(meetingId, member(meetingId).token);
        cancelLocal(meetingId);
    }

    /**
     * 서버의 내 알람을 기기 알람과 맞춘다. 약속 화면 갱신 때와 (3단계) ALARM_SYNC 푸시를 받았을 때 부른다.
     *
     * @param title     알람 화면 제목(약속 이름)
     * @param placeName 알람 화면에 보일 장소. 없으면 null
     * @param meetAtMillis 약속 시각. 모르면 0
     */
    public SyncResult syncAlarm(String meetingId, String title, String placeName, long meetAtMillis)
            throws ApiException {
        ParticipantTokenStore.Entry me = member(meetingId);
        long requestedAt = System.currentTimeMillis();
        MyAlarm my = api().myAlarm(meetingId, me.token);
        long receivedAt = System.currentTimeMillis();
        if (!my.enabled || my.alarmId == null || !my.status.shouldRing()) {
            cancelLocal(meetingId);
            return SyncResult.NO_ALARM;
        }
        long localFireAt = toDeviceTime(my.fireAtMillis, my.serverTimeMillis, (requestedAt + receivedAt) / 2);
        String previous = links.alarmFor(meetingId);
        if (previous != null && !previous.equals(my.alarmId)) {
            scheduler.cancel(previous);
        }
        AlarmScheduler.Result r = scheduler.schedule(new AlarmSpec(my.alarmId, localFireAt,
                title == null || title.isEmpty() ? "약속 출발 알람" : title, placeName, meetAtMillis));
        links.put(meetingId, my.alarmId);
        switch (r) {
            case SCHEDULED:
                return SyncResult.SCHEDULED;
            case NEEDS_EXACT_ALARM_PERMISSION:
                return SyncResult.NEEDS_EXACT_ALARM_PERMISSION;
            default:
                return SyncResult.IN_PAST;
        }
    }

    /**
     * 서버 시각 기준 시각을 기기 시계로 바꾼다. 기기 시계가 서버보다 3분 빠르면 알람도 기기 기준 3분 늦게 잡아야
     * 실제(서버) 시각에 맞게 울린다. serverTime이 없으면 보정하지 않는다.
     */
    static long toDeviceTime(long serverMillis, long serverNowMillis, long deviceNowMillis) {
        if (serverNowMillis <= 0) {
            return serverMillis;
        }
        return serverMillis + (deviceNowMillis - serverNowMillis);
    }

    /** 이 약속의 기기 알람을 지운다(약속 나가기·취소, 책임 알람 꺼짐). */
    public void cancelLocal(String meetingId) {
        String alarmId = links.alarmFor(meetingId);
        if (alarmId != null) {
            scheduler.cancel(alarmId);
            links.unlinkMeeting(meetingId);
        }
    }

    /**
     * "이미 일어남" 버튼(MANUAL_AWAKE). 알람이 울리기 전이어도 서버에 끔으로 보고하고 기기 알람을 지운다.
     * @return 서버 상태. 이미 에스컬레이션됐으면 ESCALATED
     */
    public AlarmStatus reportAwake(String meetingId) throws ApiException {
        ParticipantTokenStore.Entry me = member(meetingId);
        String alarmId = links.alarmFor(meetingId);
        if (alarmId == null) {
            MyAlarm my = api().myAlarm(meetingId, me.token);
            alarmId = my.alarmId;
        }
        if (alarmId == null) {
            throw new ApiException("ALARM_NOT_FOUND", 0, "아직 만들어진 알람이 없어요.", 0, null);
        }
        AlarmStatus status = api().dismiss(alarmId, me.token, System.currentTimeMillis(), DismissSource.MANUAL_AWAKE);
        scheduler.cancel(alarmId);
        dismissals.remove(alarmId);
        return status;
    }

    /** 울리기 시작했다고 보고(선택). 실패해도 무시한다. */
    public void reportRinging(String alarmId) {
        if (api == null) {
            return;
        }
        String meetingId = links.meetingFor(alarmId);
        ParticipantTokenStore.Entry me = meetingId == null ? null : tokens.get(meetingId);
        if (me == null) {
            return;
        }
        try {
            api.ringing(alarmId, me.token);
        } catch (ApiException e) {
            Log.i(TAG, "울림 보고 실패(무시): " + e.code);
        }
    }

    /**
     * 쌓인 끈 기록을 서버에 보낸다. 성공했거나 다시 보내도 소용없는 기록(약속이 사라짐 등)은 큐에서 지운다.
     * @return 모두 처리했으면 true, 네트워크·서버 문제로 남은 게 있으면 false(나중에 다시 시도)
     */
    public boolean flushDismissals() {
        boolean allDone = true;
        for (Map.Entry<String, Long> e : dismissals.pending().entrySet()) {
            String alarmId = e.getKey();
            String meetingId = links.meetingFor(alarmId);
            ParticipantTokenStore.Entry me = meetingId == null ? null : tokens.get(meetingId);
            if (api == null || me == null) {
                // 서버와 연결된 알람이 아님(기기 전용 알람, 나간 약속). 보낼 곳이 없다
                if (api != null) {
                    dismissals.remove(alarmId);
                    links.forgetAlarm(alarmId);
                } else {
                    allDone = false; // 서버 설정 전. 나중에 연결되면 보낸다
                }
                continue;
            }
            try {
                AlarmStatus status = api.dismiss(alarmId, me.token, e.getValue(), DismissSource.DEVICE);
                Log.i(TAG, "끔 보고 완료: " + alarmId + " → " + status);
                dismissals.remove(alarmId);
                links.forgetAlarm(alarmId);
            } catch (ApiException ex) {
                if (isRetryable(ex)) {
                    allDone = false;
                } else {
                    Log.w(TAG, "끔 보고를 버림: " + alarmId + " " + ex.code);
                    dismissals.remove(alarmId);
                    links.forgetAlarm(alarmId);
                }
            }
        }
        return allDone;
    }

    /** 네트워크·요청 제한·서버 오류는 다시 시도. 4xx(알람·약속 없음, 권한 없음)는 다시 보내도 같다. */
    static boolean isRetryable(ApiException e) {
        return e.isNetwork() || ApiException.RATE_LIMITED.equals(e.code)
                || e.httpStatus >= 500 || ApiException.BAD_RESPONSE.equals(e.code);
    }

    private AccountabilityApi api() throws ApiException {
        if (api == null) {
            throw new ApiException("NO_SERVER", 0, "서버가 연결되면 사용할 수 있어요.", 0, null);
        }
        return api;
    }

    private ParticipantTokenStore.Entry member(String meetingId) throws ApiException {
        ParticipantTokenStore.Entry e = tokens.get(meetingId);
        if (e == null) {
            throw new ApiException(MeetingRepository.NOT_MEMBER, 0,
                    "이 약속에 참가한 기록이 없어요. 초대 링크로 다시 들어와 주세요.", 0, null);
        }
        return e;
    }
}
