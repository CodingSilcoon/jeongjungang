package com.jeongjungang.data.remote.meeting;

import com.jeongjungang.data.remote.meeting.MeetingModels.Origin;
import org.json.JSONException;
import org.json.JSONObject;

/** `PATCH /participants/{id}` 본문. 설정한 필드만 보낸다. */
public final class ParticipantUpdate {

    private String nickname;
    private Origin origin;
    private Integer prepMinutes;
    private Boolean optedIn;

    public ParticipantUpdate nickname(String nickname) {
        this.nickname = nickname;
        return this;
    }

    public ParticipantUpdate origin(Origin origin) {
        this.origin = origin;
        return this;
    }

    /** 책임 알람용 준비시간 0~240분 [단계 3]. */
    public ParticipantUpdate prepMinutes(int minutes) {
        this.prepMinutes = minutes;
        return this;
    }

    /** 책임 알람 동의 [단계 3]. */
    public ParticipantUpdate optedIn(boolean optedIn) {
        this.optedIn = optedIn;
        return this;
    }

    public boolean isEmpty() {
        return nickname == null && origin == null && prepMinutes == null && optedIn == null;
    }

    /** @return 문제가 없으면 null, 있으면 사용자에게 보여 줄 문장 */
    String validate() {
        if (nickname != null) {
            String bad = MeetingRules.checkNickname(nickname);
            if (bad != null) {
                return bad;
            }
        }
        if (origin != null) {
            String bad = MeetingRules.checkOrigin(origin);
            if (bad != null) {
                return bad;
            }
        }
        if (prepMinutes != null && (prepMinutes < 0 || prepMinutes > MeetingRules.MAX_PREP_MINUTES)) {
            return "준비시간은 0~" + MeetingRules.MAX_PREP_MINUTES + "분으로 입력해 주세요.";
        }
        return null;
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        if (nickname != null) {
            o.put("nickname", nickname.trim());
        }
        if (origin != null) {
            o.put("origin", MeetingJson.originJson(origin));
        }
        if (prepMinutes != null) {
            o.put("prepMinutes", prepMinutes.intValue());
        }
        if (optedIn != null) {
            o.put("optedIn", optedIn.booleanValue());
        }
        return o;
    }
}
