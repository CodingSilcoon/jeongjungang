package com.jeongjungang.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.HttpHeaders;

/** Authorization: Bearer {token} 헤더에서 토큰을 꺼낸다. */
public final class BearerToken {

    private static final String PREFIX = "Bearer ";
    /** 발급하는 토큰은 43자다. 터무니없이 긴 값은 해시하기 전에 버린다. */
    private static final int MAX_LENGTH = 128;

    private BearerToken() {
    }

    public static Optional<String> from(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return Optional.empty();
        }
        String token = header.substring(PREFIX.length()).trim();
        if (token.isEmpty() || token.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(token);
    }
}
