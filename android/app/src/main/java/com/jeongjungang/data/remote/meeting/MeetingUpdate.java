package com.jeongjungang.data.remote.meeting;

import com.jeongjungang.data.remote.meeting.MeetingModels.Place;
import com.jeongjungang.data.remote.meeting.MeetingModels.Purpose;
import com.jeongjungang.data.remote.meeting.MeetingModels.Status;
import java.time.ZoneId;
import org.json.JSONException;
import org.json.JSONObject;

/** `PATCH /meetings/{id}` 본문. 설정한 필드만 보낸다. */
public final class MeetingUpdate {

    private String title;
    private Long meetAtMillis;
    private Purpose purpose;
    private Place place;
    private Status status;

    public MeetingUpdate title(String title) {
        this.title = title;
        return this;
    }

    public MeetingUpdate meetAt(long millis) {
        this.meetAtMillis = millis;
        return this;
    }

    public MeetingUpdate purpose(Purpose purpose) {
        this.purpose = purpose;
        return this;
    }

    public MeetingUpdate place(Place place) {
        this.place = place;
        return this;
    }

    /** 장소·시간 확정. 서버는 place와 meetAt이 있어야 받아 준다. */
    public MeetingUpdate confirm() {
        this.status = Status.CONFIRMED;
        return this;
    }

    /** 확정 되돌리기. */
    public MeetingUpdate reopen() {
        this.status = Status.OPEN;
        return this;
    }

    public boolean isEmpty() {
        return title == null && meetAtMillis == null && purpose == null && place == null && status == null;
    }

    JSONObject toJson(ZoneId zone) throws JSONException {
        JSONObject o = new JSONObject();
        if (title != null) {
            o.put("title", title);
        }
        if (meetAtMillis != null) {
            o.put("meetAt", MeetingJson.isoTime(meetAtMillis, zone));
        }
        if (purpose != null) {
            o.put("purpose", purpose.name());
        }
        if (place != null) {
            o.put("place", MeetingJson.placeJson(place));
        }
        if (status != null) {
            o.put("status", status.name());
        }
        return o;
    }
}
