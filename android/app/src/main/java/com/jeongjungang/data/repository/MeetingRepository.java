package com.jeongjungang.data.repository;

import android.content.Context;
import com.jeongjungang.BuildConfig;
import com.jeongjungang.data.remote.ApiException;
import com.jeongjungang.data.remote.meeting.HttpMeetingApi;
import com.jeongjungang.data.remote.meeting.MeetingApi;
import com.jeongjungang.data.remote.meeting.MeetingModels.InvitePreview;
import com.jeongjungang.data.remote.meeting.MeetingModels.Membership;
import com.jeongjungang.data.remote.meeting.MeetingModels.Origin;
import com.jeongjungang.data.remote.meeting.MeetingModels.PlacePage;
import com.jeongjungang.data.remote.meeting.MeetingModels.Purpose;
import com.jeongjungang.data.remote.meeting.MeetingModels.Snapshot;
import com.jeongjungang.data.remote.meeting.MeetingUpdate;
import com.jeongjungang.data.remote.meeting.ParticipantUpdate;
import com.jeongjungang.domain.model.LatLng;
import java.util.List;

/**
 * 약속 API와 토큰 저장소를 묶는다. 화면 쪽은 meetingId만 알면 되고, 토큰은 여기서 넣는다.
 * 서버 주소(local.properties의 jeongjungang.apiBaseUrl)가 없으면 {@link #isAvailable()}이 false다.
 * 모든 메서드는 네트워크 호출이라 백그라운드에서 부른다.
 */
public final class MeetingRepository {

    /** 이 약속의 토큰이 없음(앱 재설치, 나간 약속 등). 다시 참가해야 한다. */
    public static final String NOT_MEMBER = "NOT_MEMBER";

    private static volatile MeetingRepository instance;

    private final MeetingApi api;
    private final ParticipantTokenStore tokens;

    MeetingRepository(MeetingApi api, ParticipantTokenStore tokens) {
        this.api = api;
        this.tokens = tokens;
    }

    public static MeetingRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (MeetingRepository.class) {
                if (instance == null) {
                    MeetingApi api = BuildConfig.API_BASE_URL.isEmpty()
                            ? null : new HttpMeetingApi(BuildConfig.API_BASE_URL);
                    instance = new MeetingRepository(api, new ParticipantTokenStore(context));
                }
            }
        }
        return instance;
    }

    /** 서버가 설정돼 있으면 true. false면 화면에서 "서버 연결 후 사용할 수 있어요"를 보여 준다. */
    public boolean isAvailable() {
        return api != null;
    }

    public Membership create(String hostNickname, String title, Purpose purpose, Long meetAtMillis, Origin origin)
            throws ApiException {
        Membership m = api().create(hostNickname, title, purpose, meetAtMillis, origin);
        tokens.save(m.meetingId, m.participantId, m.token);
        return m;
    }

    public InvitePreview preview(String inviteCode) throws ApiException {
        return api().preview(inviteCode);
    }

    public Membership join(String inviteCode, String nickname, Origin origin) throws ApiException {
        Membership m = api().join(inviteCode, nickname, origin);
        tokens.save(m.meetingId, m.participantId, m.token);
        return m;
    }

    /**
     * @return 새 상태, 지난 etag 이후 바뀐 게 없으면 null
     * @throws ApiException 약속이 사라졌거나 내가 빠졌으면 토큰도 지운다
     *                      (MEETING_NOT_FOUND, MEETING_EXPIRED, UNAUTHORIZED, NOT_MEMBER)
     */
    public Snapshot snapshot(String meetingId, String etag) throws ApiException {
        ParticipantTokenStore.Entry me = member(meetingId);
        try {
            return api().get(meetingId, me.token, etag);
        } catch (ApiException e) {
            if (isGone(e)) {
                tokens.remove(meetingId);
            }
            throw e;
        }
    }

    public void update(String meetingId, MeetingUpdate update) throws ApiException {
        api().update(meetingId, member(meetingId).token, update);
    }

    /** 방장이 약속을 취소한다. 내 토큰도 지운다. */
    public void cancel(String meetingId) throws ApiException {
        api().cancel(meetingId, member(meetingId).token);
        tokens.remove(meetingId);
    }

    /** 내 이름·출발지·준비시간·동의를 바꾼다. */
    public void updateMe(String meetingId, ParticipantUpdate update) throws ApiException {
        ParticipantTokenStore.Entry me = member(meetingId);
        api().updateParticipant(me.participantId, me.token, update);
    }

    /**
     * 약속에서 나간다. 방장이면 가장 먼저 들어온 사람에게 방장이 넘어가고, 혼자였으면 약속이 취소된다(서버 처리).
     * 서버가 이미 나를 모르면(404·401) 나간 것으로 보고 토큰만 지운다.
     */
    public void leave(String meetingId) throws ApiException {
        ParticipantTokenStore.Entry me = member(meetingId);
        try {
            api().removeParticipant(me.participantId, me.token);
        } catch (ApiException e) {
            if (!isGone(e)) {
                throw e;
            }
        }
        tokens.remove(meetingId);
    }

    /** 방장이 다른 참가자를 내보낸다. */
    public void kick(String meetingId, String participantId) throws ApiException {
        api().removeParticipant(participantId, member(meetingId).token);
    }

    public PlacePage places(LatLng center, String category, int radiusMeters, int page) throws ApiException {
        return api().places(center, category, radiusMeters, page);
    }

    /** 이 기기에서 들어가 있는 약속 id들. */
    public List<String> myMeetingIds() {
        return tokens.meetingIds();
    }

    public boolean isMember(String meetingId) {
        return tokens.get(meetingId) != null;
    }

    private MeetingApi api() throws ApiException {
        if (api == null) {
            throw new ApiException("NO_SERVER", 0, "서버가 연결되면 사용할 수 있어요.", 0, null);
        }
        return api;
    }

    private ParticipantTokenStore.Entry member(String meetingId) throws ApiException {
        ParticipantTokenStore.Entry e = tokens.get(meetingId);
        if (e == null) {
            throw new ApiException(NOT_MEMBER, 0, "이 약속에 참가한 기록이 없어요. 초대 링크로 다시 들어와 주세요.", 0, null);
        }
        return e;
    }

    /** 약속이 없어졌거나 내 자격이 없어진 오류. */
    static boolean isGone(ApiException e) {
        return "MEETING_NOT_FOUND".equals(e.code) || "MEETING_EXPIRED".equals(e.code)
                || "PARTICIPANT_NOT_FOUND".equals(e.code) || "UNAUTHORIZED".equals(e.code)
                || e.httpStatus == 401 || e.httpStatus == 410;
    }
}
