package com.jeongjungang.data.remote.meeting;

import com.jeongjungang.data.remote.ApiException;
import com.jeongjungang.data.remote.ApiHttp;
import java.time.ZoneId;
import org.json.JSONException;
import org.json.JSONObject;

/** {@link AccountabilityApi}의 서버 구현. */
public final class HttpAccountabilityApi implements AccountabilityApi {

    /** docs/API.md: gracePeriodSec 30~300, 기본 60. */
    public static final int MIN_GRACE_SEC = 30;
    public static final int MAX_GRACE_SEC = 300;
    public static final int DEFAULT_GRACE_SEC = 60;

    private final ApiHttp http;
    private final ZoneId zone;

    public HttpAccountabilityApi(String baseUrl) {
        this(new ApiHttp(baseUrl), ZoneId.systemDefault());
    }

    HttpAccountabilityApi(ApiHttp http, ZoneId zone) {
        this.http = http;
        this.zone = zone;
    }

    @Override
    public void reportTravel(String participantId, String token, String placeName, double minutes, int transfers)
            throws ApiException {
        if (placeName == null || Double.isNaN(minutes) || minutes < 0 || transfers < 0) {
            throw new ApiException("VALIDATION_FAILED", 0, "이동시간을 계산하지 못했어요.", 0, null);
        }
        try {
            JSONObject body = new JSONObject()
                    .put("placeName", placeName)
                    // 0.1분 단위면 충분하다(서버는 분 단위로 올림해 쓴다)
                    .put("minutes", Math.round(minutes * 10) / 10.0)
                    .put("transfers", transfers);
            http.put("/participants/" + ApiHttp.pathSegment(participantId) + "/travel", body, token);
        } catch (JSONException e) {
            throw ApiHttp.badResponse(e);
        }
    }

    @Override
    public void enable(String meetingId, String token, int gracePeriodSec, int marginMinutes) throws ApiException {
        if (gracePeriodSec < MIN_GRACE_SEC || gracePeriodSec > MAX_GRACE_SEC) {
            throw new ApiException("VALIDATION_FAILED", 0,
                    "유예시간은 " + MIN_GRACE_SEC + "~" + MAX_GRACE_SEC + "초로 정해 주세요.", 0, null);
        }
        if (marginMinutes < 0) {
            throw new ApiException("VALIDATION_FAILED", 0, "여유시간은 0분 이상이어야 해요.", 0, null);
        }
        try {
            http.post("/meetings/" + ApiHttp.pathSegment(meetingId) + "/accountability", new JSONObject()
                    .put("gracePeriodSec", gracePeriodSec)
                    .put("marginMinutes", marginMinutes), token);
        } catch (JSONException e) {
            throw ApiHttp.badResponse(e);
        }
    }

    @Override
    public void disable(String meetingId, String token) throws ApiException {
        http.delete("/meetings/" + ApiHttp.pathSegment(meetingId) + "/accountability", token);
    }

    @Override
    public void registerDevice(String token, String fcmToken, String appVersion) throws ApiException {
        try {
            http.post("/devices", new JSONObject()
                    .put("fcmToken", fcmToken)
                    .put("platform", "ANDROID")
                    .put("appVersion", appVersion), token);
        } catch (JSONException e) {
            throw ApiHttp.badResponse(e);
        }
    }

    @Override
    public MyAlarm myAlarm(String meetingId, String token) throws ApiException {
        JSONObject data = http.get("/meetings/" + ApiHttp.pathSegment(meetingId) + "/alarms", token, null).data;
        try {
            JSONObject acc = data.optJSONObject("accountability");
            JSONObject alarm = data.optJSONObject("alarm");
            Long serverTime = MeetingJson.time(data, "serverTime");
            boolean enabled = acc != null && acc.optBoolean("enabled", false);
            int grace = acc == null ? DEFAULT_GRACE_SEC : acc.optInt("gracePeriodSec", DEFAULT_GRACE_SEC);
            if (alarm == null) {
                return new MyAlarm(enabled, grace, null, 0, 0, AlarmStatus.UNKNOWN,
                        serverTime == null ? 0 : serverTime);
            }
            Long fireAt = MeetingJson.time(alarm, "fireAt");
            Long graceUntil = MeetingJson.time(alarm, "graceUntil");
            if (fireAt == null) {
                throw new JSONException("fireAt 없음");
            }
            return new MyAlarm(enabled, grace, alarm.getString("id"), fireAt,
                    graceUntil == null ? fireAt + grace * 1000L : graceUntil,
                    AlarmStatus.parse(ApiHttp.optString(alarm, "status")), serverTime == null ? 0 : serverTime);
        } catch (JSONException e) {
            throw ApiHttp.badResponse(e);
        }
    }

    @Override
    public void ringing(String alarmId, String token) throws ApiException {
        http.post("/alarms/" + ApiHttp.pathSegment(alarmId) + "/ringing", null, token);
    }

    @Override
    public AlarmStatus dismiss(String alarmId, String token, long dismissedAtMillis, DismissSource source)
            throws ApiException {
        try {
            JSONObject data = http.post("/alarms/" + ApiHttp.pathSegment(alarmId) + "/dismiss", new JSONObject()
                    .put("dismissedAt", MeetingJson.isoTime(dismissedAtMillis, zone))
                    .put("source", source.name()), token).data;
            return data == null ? AlarmStatus.UNKNOWN : AlarmStatus.parse(ApiHttp.optString(data, "status"));
        } catch (JSONException e) {
            throw ApiHttp.badResponse(e);
        }
    }
}
