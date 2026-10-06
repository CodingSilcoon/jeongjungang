package com.jeongjungang.meeting;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** 초대 코드: 8자리, 헷갈리는 글자(I L O U 0 1)는 쓰지 않는다 (docs/API.md 2절). */
final class InviteCodes {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789";
    static final int LENGTH = 8;
    private static final Pattern VALID = Pattern.compile("[" + ALPHABET + "]{" + LENGTH + "}");
    private static final Pattern IGNORED = Pattern.compile("[\s-]");
    private static final SecureRandom RANDOM = new SecureRandom();

    private InviteCodes() {
    }

    static String random() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** 소문자·공백·하이픈은 허용하고 대문자 8자리로 맞춘다. 형식이 아니면 빈 값. */
    static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String code = IGNORED.matcher(raw).replaceAll("").toUpperCase(Locale.ROOT);
        return VALID.matcher(code).matches() ? Optional.of(code) : Optional.empty();
    }
}
