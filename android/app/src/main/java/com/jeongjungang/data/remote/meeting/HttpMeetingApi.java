package com.jeongjungang.data.remote.meeting;

import com.jeongjungang.data.remote.ApiException;
import com.jeongjungang.data.remote.ApiHttp;
import com.jeongjungang.data.remote.meeting.MeetingModels.InvitePreview;
import com.jeongjungang.data.remote.meeting.MeetingModels.Membership;
import com.jeongjungang.data.remote.meeting.MeetingModels.Origin;
import com.jeongjungang.data.remote.meeting.MeetingModels.PlacePage;
import com.jeongjungang.data.remote.meeting.MeetingModels.Purpose;
import com.jeongjungang.data.remote.meeting.MeetingModels.Snapshot;
import com.jeongjungang.domain.model.LatLng;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;

/** {@link MeetingApi}의 서버 구현. */
public final class HttpMeetingApi implements MeetingApi {

    /** docs/API.md `GET /places` radius 범위. */
    public static final int MIN_RADIUS = 100;
    public static final int MAX_RADIUS = 1000;
    public static final int DEFAULT_RADIUS = 500;
    /** docs/API.md `GET /places` page 1~45 (카카오 검색 한도). */
    public static final int MAX_PLACE_PAGE = 45;

    private final ApiHttp http;
    private final ZoneId zone;
    private final Clock clock;

    public HttpMeetingApi(String baseUrl) {
        this(new ApiHttp(baseUrl), ZoneId.systemDefault());
    }

    HttpMeetingApi(ApiHttp http, ZoneId zone) {
        this(http, zone, Clock.systemUTC());
    }

    HttpMeetingApi(ApiHttp http, ZoneId zone, Clock clock) {
        this.http = http;
        this.zone = zone;
        this.clock = clock;
    }

    @Override
    public Membership create(String hostNickname, String title, Purpose purpose, Long meetAtMillis, Origin origin)
            throws ApiException {
        requireValid(MeetingRules.checkNickname(hostNickname));
        requireValid(MeetingRules.checkTitle(title));
        if (origin != null) {
            requireValid(MeetingRules.checkOrigin(origin));
        }
        if (meetAtMillis != null) {
            requireMeetAt(meetAtMillis);
        }
        try {
            JSONObject body = new JSONObject().put("hostNickname", hostNickname.trim());
            if (title != null && !title.trim().isEmpty()) {
                body.put("title", title.trim());
            }
            if (purpose != null) {
                body.put("purpose", purpose.name());
            }
            if (meetAtMillis != null) {
                body.put("meetAt", MeetingJson.isoTime(meetAtMillis, zone));
            }
            if (origin != null) {
                body.put("origin", MeetingJson.originJson(origin));
            }
            return MeetingJson.created(http.post("/meetings", body, null).data);
        } catch (JSONException e) {
            throw ApiHttp.badResponse(e);
        }
    }

    @Override
    public InvitePreview preview(String inviteCode) throws ApiException {
        String code = requireCode(inviteCode);
        try {
            return MeetingJson.preview(http.get("/meetings/by-code/" + code, null, null).data);
        } catch (JSONException e) {
            throw ApiHttp.badResponse(e);
        }
    }

    @Override
    public Membership join(String inviteCode, String nickname, Origin origin) throws ApiException {
        String code = requireCode(inviteCode);
        requireValid(MeetingRules.checkNickname(nickname));
        if (origin != null) {
            requireValid(MeetingRules.checkOrigin(origin));
        }
        try {
            JSONObject body = new JSONObject().put("nickname", nickname.trim());
            if (origin != null) {
                body.put("origin", MeetingJson.originJson(origin));
            }
            return MeetingJson.joined(http.post("/meetings/by-code/" + code + "/participants", body, null).data);
        } catch (JSONException e) {
            throw ApiHttp.badResponse(e);
        }
    }

    @Override
    public Snapshot get(String meetingId, String token, String etag) throws ApiException {
        ApiHttp.Result r = http.get("/meetings/" + ApiHttp.pathSegment(meetingId), token, etag);
        if (r.notModified) {
            return null;
        }
        try {
            return MeetingJson.snapshot(r.data, r.etag);
        } catch (JSONException e) {
            throw ApiHttp.badResponse(e);
        }
    }

    @Override
    public void update(String meetingId, String token, MeetingUpdate update) throws ApiException {
        if (update.isEmpty()) {
            return;
        }
        if (update.meetAtMillis() != null) {
            requireMeetAt(update.meetAtMillis());
        }
        try {
            http.patch("/meetings/" + ApiHttp.pathSegment(meetingId), update.toJson(zone), token);
        } catch (JSONException e) {
            throw ApiHttp.badResponse(e);
        }
    }

    @Override
    public void cancel(String meetingId, String token) throws ApiException {
        http.delete("/meetings/" + ApiHttp.pathSegment(meetingId), token);
    }

    @Override
    public void updateParticipant(String participantId, String token, ParticipantUpdate update)
            throws ApiException {
        if (update.isEmpty()) {
            return;
        }
        requireValid(update.validate());
        try {
            http.patch("/participants/" + ApiHttp.pathSegment(participantId), update.toJson(), token);
        } catch (JSONException e) {
            throw ApiHttp.badResponse(e);
        }
    }

    @Override
    public void removeParticipant(String participantId, String token) throws ApiException {
        http.delete("/participants/" + ApiHttp.pathSegment(participantId), token);
    }

    @Override
    public PlacePage places(LatLng center, String category, int radiusMeters, int page) throws ApiException {
        if (category == null) {
            throw new ApiException("VALIDATION_FAILED", 0, "장소 종류를 골라 주세요.", 0, null);
        }
        int radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radiusMeters));
        String query = String.format(Locale.US, "/places?lat=%.6f&lng=%.6f&category=%s&radius=%d&page=%d",
                center.lat, center.lng, category, radius, Math.max(1, Math.min(MAX_PLACE_PAGE, page)));
        try {
            return MeetingJson.places(http.get(query, null, null).data);
        } catch (JSONException e) {
            throw ApiHttp.badResponse(e);
        }
    }

    private static String requireCode(String input) throws ApiException {
        String code = MeetingRules.parseInviteCode(input);
        if (code == null) {
            throw new ApiException("VALIDATION_FAILED", 0, "초대 코드 8자리를 확인해 주세요.", 0, null);
        }
        return code;
    }

    /** 서버와 같은 필드 이름(meetAt)으로 사유를 담아, 화면이 서버 오류와 똑같이 다룰 수 있게 한다. */
    private void requireMeetAt(long meetAtMillis) throws ApiException {
        String problem = MeetingRules.checkMeetAt(meetAtMillis, clock.millis());
        if (problem != null) {
            throw new ApiException("VALIDATION_FAILED", 0, problem, 0, null,
                    java.util.Collections.singletonMap("meetAt", problem));
        }
    }

    private static void requireValid(String problem) throws ApiException {
        if (problem != null) {
            throw new ApiException("VALIDATION_FAILED", 0, problem, 0, null);
        }
    }
}
