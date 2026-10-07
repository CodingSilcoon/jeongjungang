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
    /** docs/API.md: meetAt은 지금부터 1시간 전 ~ 1년(365일) 뒤. 서버 MeetingService와 같은 값. */
    public static final long MEET_AT_PAST_LIMIT_MS = 60L * 60 * 1000;
    public static final long MEET_AT_FUTURE_LIMIT_MS = 365L * 24 * 60 * 60 * 1000;
    /** 서버가 쓰는 문장과 같게 맞춘다. */
    static final String MEET_AT_MESSAGE = "약속 시간은 지금부터 1년 안으로 정해 주세요.";
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
        if (hasInvisibleOrControlChar(n)) {
            return "이름에 쓸 수 없는 글자가 있어요.";
        }
        return null;
    }

    /**
     * 약속 시각 범위 (지금부터 1시간 전 ~ 1년 뒤). 기기 시계 기준이라 경계 근처는 서버가 최종 판단한다.
     * @return 문제가 없으면 null, 있으면 사용자에게 보여 줄 문장
     */
    public static String checkMeetAt(long meetAtMillis, long nowMillis) {
        if (meetAtMillis < nowMillis - MEET_AT_PAST_LIMIT_MS || meetAtMillis > nowMillis + MEET_AT_FUTURE_LIMIT_MS) {
            return MEET_AT_MESSAGE;
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

    /**
     * 제어문자와 보이지 않는 글자(폭 없는 공백, 방향 바꾸기 등 FORMAT, 줄·문단 구분자). 서버 `@Nickname`과 같은 규칙.
     * 이모지 결합용 ZWJ도 FORMAT이라 막힌다(서버와 같게 둔다).
     */
    private static boolean hasInvisibleOrControlChar(String s) {
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int type = Character.getType(cp);
            if (type == Character.CONTROL || type == Character.FORMAT
                    || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }
}
