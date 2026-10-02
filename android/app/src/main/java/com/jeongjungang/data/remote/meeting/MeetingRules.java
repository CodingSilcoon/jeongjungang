package com.jeongjungang.data.remote.meeting;

import com.jeongjungang.data.remote.meeting.MeetingModels.Origin;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * docs/API.md 값 제한과 초대 코드 규칙. 서버도 같은 검사를 하지만, 앱에서 먼저 막아 바로 안내한다.
 */
public final class MeetingRules {

    public static final int MAX_NICKNAME = 20;
    public static final int MAX_TITLE = 40;
    public static final int MAX_ORIGIN_LABEL = 60;
    public static final int MAX_PREP_MINUTES = 240;
    public static final int MAX_PARTICIPANTS = 10;
    public static final int INVITE_CODE_LENGTH = 8;
    /** 혼동되는 글자 I L O U 0 1을 뺀 알파벳. */
    static final String INVITE_ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789";

    /** 초대 링크 경로: https://{도메인}/m/{초대코드} */
    private static final Pattern INVITE_URL = Pattern.compile("^https?://[^/]+/m/([^/?#]+)/?(?:[?#].*)?$",
            Pattern.CASE_INSENSITIVE);

    private MeetingRules() {}

    /** @return 문제가 없으면 null, 있으면 사용자에게 보여 줄 문장 */
    public static String checkNickname(String nickname) {
        String n = nickname == null ? "" : nickname.trim();
        if (n.isEmpty()) {
            return "이름을 입력해 주세요.";
        }
        if (n.codePointCount(0, n.length()) > MAX_NICKNAME) {
            return "이름은 " + MAX_NICKNAME + "자까지 입력할 수 있어요.";
        }
        if (hasControlChar(n)) {
            return "이름에 쓸 수 없는 글자가 있어요.";
        }
        return null;
    }

    public static String checkTitle(String title) {
        if (title != null && title.trim().codePointCount(0, title.trim().length()) > MAX_TITLE) {
            return "약속 이름은 " + MAX_TITLE + "자까지 입력할 수 있어요.";
        }
        return null;
    }

    public static String checkOrigin(Origin origin) {
        String label = origin.label.trim();
        if (label.isEmpty() || label.codePointCount(0, label.length()) > MAX_ORIGIN_LABEL) {
            return "출발지 이름은 1~" + MAX_ORIGIN_LABEL + "자여야 해요.";
        }
        if (Math.abs(origin.location.lat) > 90 || Math.abs(origin.location.lng) > 180) {
            return "출발지 좌표가 올바르지 않아요.";
        }
        return null;
    }

    /**
     * 사용자가 붙여 넣은 초대 링크나 코드에서 초대 코드를 꺼낸다.
     * 소문자, 공백, 하이픈은 허용한다("7k3q-h9mx" → "7K3QH9MX").
     * @return 올바른 코드, 아니면 null
     */
    public static String parseInviteCode(String input) {
        if (input == null) {
            return null;
        }
        String s = input.trim();
        Matcher m = INVITE_URL.matcher(s);
        if (m.matches()) {
            s = m.group(1);
        }
        s = s.replace(" ", "").replace("-", "").toUpperCase(Locale.ROOT);
        if (s.length() != INVITE_CODE_LENGTH) {
            return null;
        }
        for (int i = 0; i < s.length(); i++) {
            if (INVITE_ALPHABET.indexOf(s.charAt(i)) < 0) {
                return null;
            }
        }
        return s;
    }

    private static boolean hasControlChar(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isISOControl(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
