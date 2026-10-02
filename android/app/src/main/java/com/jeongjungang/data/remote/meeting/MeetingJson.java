package com.jeongjungang.data.remote.meeting;

import com.jeongjungang.data.remote.ApiHttp;
import com.jeongjungang.data.remote.meeting.MeetingModels.Accountability;
import com.jeongjungang.data.remote.meeting.MeetingModels.InvitePreview;
import com.jeongjungang.data.remote.meeting.MeetingModels.Meeting;
import com.jeongjungang.data.remote.meeting.MeetingModels.Member;
import com.jeongjungang.data.remote.meeting.MeetingModels.Membership;
import com.jeongjungang.data.remote.meeting.MeetingModels.NearbyPlace;
import com.jeongjungang.data.remote.meeting.MeetingModels.Origin;
import com.jeongjungang.data.remote.meeting.MeetingModels.Place;
import com.jeongjungang.data.remote.meeting.MeetingModels.PlacePage;
import com.jeongjungang.data.remote.meeting.MeetingModels.Purpose;
import com.jeongjungang.data.remote.meeting.MeetingModels.Role;
import com.jeongjungang.data.remote.meeting.MeetingModels.Snapshot;
import com.jeongjungang.data.remote.meeting.MeetingModels.Status;
import com.jeongjungang.domain.model.LatLng;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** docs/API.md JSON ↔ 모델. 시각은 ISO 8601 + 오프셋 문자열과 epoch ms 사이를 바꾼다. */
final class MeetingJson {

    private MeetingJson() {}

    // ---- 읽기 ----

    static Meeting meeting(JSONObject o) throws JSONException {
        JSONObject acc = o.optJSONObject("accountability");
        return new Meeting(
                o.getString("id"),
                ApiHttp.optString(o, "inviteCode"),
                ApiHttp.optString(o, "inviteUrl"),
                ApiHttp.optString(o, "title"),
                Purpose.parse(ApiHttp.optString(o, "purpose")),
                time(o, "meetAt"),
                Status.parse(ApiHttp.optString(o, "status")),
                place(o.optJSONObject("place")),
                time(o, "expiresAt"),
                acc == null ? null : new Accountability(acc.optBoolean("enabled", false),
                        acc.optInt("gracePeriodSec", 60), acc.optInt("marginMinutes", 0)));
    }

    static Snapshot snapshot(JSONObject data, String etag) throws JSONException {
        JSONArray arr = data.getJSONArray("participants");
        List<Member> members = new ArrayList<Member>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject p = arr.getJSONObject(i);
            members.add(new Member(
                    p.getString("id"),
                    p.getString("nickname"),
                    Role.parse(ApiHttp.optString(p, "role")),
                    origin(p.optJSONObject("origin")),
                    p.isNull("prepMinutes") || !p.has("prepMinutes") ? null : p.getInt("prepMinutes"),
                    p.optBoolean("optedIn", false)));
        }
        JSONObject me = data.getJSONObject("me");
        Long serverTime = time(data, "serverTime");
        return new Snapshot(data.getLong("version"), meeting(data.getJSONObject("meeting")), members,
                me.getString("participantId"), Role.parse(ApiHttp.optString(me, "role")),
                serverTime == null ? 0 : serverTime, etag);
    }

    static InvitePreview preview(JSONObject o) throws JSONException {
        return new InvitePreview(ApiHttp.optString(o, "title"), o.getString("hostNickname"),
                o.getInt("participantCount"), Status.parse(ApiHttp.optString(o, "status")), time(o, "meetAt"));
    }

    /** `POST /meetings` 응답. */
    static Membership created(JSONObject data) throws JSONException {
        Meeting m = meeting(data.getJSONObject("meeting"));
        JSONObject p = data.getJSONObject("participant");
        return new Membership(m.id, p.getString("id"), Role.parse(ApiHttp.optString(p, "role")),
                data.getString("participantToken"), m);
    }

    /** `POST /meetings/by-code/{code}/participants` 응답. */
    static Membership joined(JSONObject data) throws JSONException {
        JSONObject p = data.getJSONObject("participant");
        return new Membership(data.getString("meetingId"), p.getString("id"),
                Role.parse(ApiHttp.optString(p, "role")), data.getString("participantToken"), null);
    }

    static PlacePage places(JSONObject data) throws JSONException {
        JSONArray arr = data.getJSONArray("items");
        List<NearbyPlace> items = new ArrayList<NearbyPlace>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            items.add(new NearbyPlace(o.getString("name"), ApiHttp.optString(o, "category"),
                    ApiHttp.optString(o, "address"), new LatLng(o.getDouble("lat"), o.getDouble("lng")),
                    o.optInt("distanceMeters", 0), ApiHttp.optString(o, "placeUrl")));
        }
        return new PlacePage(items, data.optInt("page", 1), data.optBoolean("hasNext", false));
    }

    static Origin origin(JSONObject o) throws JSONException {
        if (o == null) {
            return null;
        }
        return new Origin(o.getString("label"), new LatLng(o.getDouble("lat"), o.getDouble("lng")));
    }

    static Place place(JSONObject o) throws JSONException {
        if (o == null) {
            return null;
        }
        List<Integer> lines = new ArrayList<Integer>();
        JSONArray arr = o.optJSONArray("lines");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                lines.add(arr.getInt(i));
            }
        }
        return new Place(o.getString("name"), new LatLng(o.getDouble("lat"), o.getDouble("lng")), lines);
    }

    /** ISO 8601(오프셋 포함)을 epoch ms로. 없거나 null이면 null. */
    static Long time(JSONObject o, String key) throws JSONException {
        String s = ApiHttp.optString(o, key);
        if (s == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(s).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            throw new JSONException("시각 형식이 아닙니다: " + key + "=" + s);
        }
    }

    // ---- 쓰기 ----

    /** epoch ms를 기기 시간대 오프셋을 붙인 ISO 8601로. 예: 2026-10-10T19:00:00+09:00 */
    static String isoTime(long millis, ZoneId zone) {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
                .withNano(0)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    static JSONObject originJson(Origin origin) throws JSONException {
        return new JSONObject()
                .put("label", origin.label)
                .put("lat", origin.location.lat)
                .put("lng", origin.location.lng);
    }

    static JSONObject placeJson(Place place) throws JSONException {
        JSONArray lines = new JSONArray();
        for (int line : place.lines) {
            lines.put(line);
        }
        return new JSONObject()
                .put("name", place.name)
                .put("lat", place.location.lat)
                .put("lng", place.location.lng)
                .put("lines", lines);
    }
}
