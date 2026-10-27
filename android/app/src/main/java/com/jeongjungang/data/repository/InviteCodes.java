package com.jeongjungang.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 이 기기로 들어간 약속의 초대 코드 → 약속 id.
 * 이미 들어간 약속의 초대 링크를 다시 누르면 참가 화면 대신 그 약속으로 보내려고 둔다.
 * 토큰이 아니라 코드만 있어서 비밀이 아니다. 실제로 아직 멤버인지는 {@link MeetingRepository}가 토큰으로 확인한다.
 */
final class InviteCodes {

    private final SharedPreferences prefs;

    InviteCodes(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("invite_codes", Context.MODE_PRIVATE);
    }

    void put(String inviteCode, String meetingId) {
        if (inviteCode == null || meetingId == null) {
            return;
        }
        if (!meetingId.equals(prefs.getString(inviteCode, null))) {
            prefs.edit().putString(inviteCode, meetingId).apply();
        }
    }

    String meetingFor(String inviteCode) {
        return inviteCode == null ? null : prefs.getString(inviteCode, null);
    }
}
