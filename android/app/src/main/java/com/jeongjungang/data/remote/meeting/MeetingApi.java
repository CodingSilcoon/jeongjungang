package com.jeongjungang.data.remote.meeting;

import com.jeongjungang.data.remote.ApiException;
import com.jeongjungang.data.remote.meeting.MeetingModels.InvitePreview;
import com.jeongjungang.data.remote.meeting.MeetingModels.Membership;
import com.jeongjungang.data.remote.meeting.MeetingModels.Origin;
import com.jeongjungang.data.remote.meeting.MeetingModels.PlacePage;
import com.jeongjungang.data.remote.meeting.MeetingModels.Purpose;
import com.jeongjungang.data.remote.meeting.MeetingModels.Snapshot;
import com.jeongjungang.domain.model.LatLng;

/**
 * 약속·초대 API (docs/API.md 6절). 모두 네트워크 호출이라 메인 스레드에서 부르지 않는다.
 * 토큰이 필요한 호출은 {@code MeetingRepository}가 저장한 토큰을 넣어 준다.
 */
public interface MeetingApi {

    /** `POST /meetings`. title·purpose·meetAt·origin은 null이면 보내지 않는다. */
    Membership create(String hostNickname, String title, Purpose purpose, Long meetAtMillis, Origin origin)
            throws ApiException;

    /** `GET /meetings/by-code/{code}`. 공개. */
    InvitePreview preview(String inviteCode) throws ApiException;

    /** `POST /meetings/by-code/{code}/participants`. origin은 null 가능(나중에 넣기). */
    Membership join(String inviteCode, String nickname, Origin origin) throws ApiException;

    /**
     * `GET /meetings/{id}`.
     * @param etag 지난 응답의 {@link Snapshot#etag}. 바뀐 게 없으면 null을 돌려준다(304)
     * @return 새 상태, 바뀐 게 없으면 null
     */
    Snapshot get(String meetingId, String token, String etag) throws ApiException;

    /** `PATCH /meetings/{id}`. 방장만. */
    void update(String meetingId, String token, MeetingUpdate update) throws ApiException;

    /** `DELETE /meetings/{id}`. 방장만. 약속 취소. */
    void cancel(String meetingId, String token) throws ApiException;

    /** `PATCH /participants/{id}`. 본인만. */
    void updateParticipant(String participantId, String token, ParticipantUpdate update) throws ApiException;

    /**
     * `DELETE /participants/{id}`. 본인이면 나가기, 방장이면 내보내기.
     * 방장이 나가면 가장 먼저 들어온 사람에게 방장이 넘어가고, 혼자였으면 약속이 취소된다.
     */
    void removeParticipant(String participantId, String token) throws ApiException;

    /** `GET /places`. 공개. category는 FOOD·CAFE·BAR ({@link Purpose#placeCategory}). */
    PlacePage places(LatLng center, String category, int radiusMeters, int page) throws ApiException;
}
