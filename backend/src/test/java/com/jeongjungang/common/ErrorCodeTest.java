package com.jeongjungang.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

class ErrorCodeTest {

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void everyCodeHasAUserFacingMessageAndStatus(ErrorCode code) {
        assertThat(code.message()).isNotBlank();
        assertThat(code.status()).isNotNull();
    }

    @Test
    void statusesMatchTheApiSpec() {
        assertThat(ErrorCode.VALIDATION_FAILED.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.UNAUTHORIZED.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.FORBIDDEN.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.MEETING_NOT_FOUND.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.MEETING_CLOSED.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.MEETING_FULL.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.NOT_ALL_OPTED_IN.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.ALARM_NOT_READY.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.MEETING_EXPIRED.status()).isEqualTo(HttpStatus.GONE);
        assertThat(ErrorCode.RATE_LIMITED.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ErrorCode.UPSTREAM_ERROR.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(ErrorCode.INTERNAL_ERROR.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
