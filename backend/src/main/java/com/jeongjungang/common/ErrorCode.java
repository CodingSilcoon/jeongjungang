package com.jeongjungang.common;

import org.springframework.http.HttpStatus;

/** API 오류 코드. 표는 docs/API.md 3절과 맞춘다. message는 사용자에게 그대로 보여도 되는 문장만 쓴다. */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증 정보가 없거나 올바르지 않습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "이 작업을 할 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없습니다."),
    MEETING_NOT_FOUND(HttpStatus.NOT_FOUND, "약속을 찾을 수 없습니다."),
    PARTICIPANT_NOT_FOUND(HttpStatus.NOT_FOUND, "참가자를 찾을 수 없습니다."),
    ALARM_NOT_FOUND(HttpStatus.NOT_FOUND, "알람을 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    MEETING_CLOSED(HttpStatus.CONFLICT, "이미 확정되었거나 취소된 약속입니다."),
    MEETING_FULL(HttpStatus.CONFLICT, "참가 인원이 가득 찼습니다."),
    NOT_ALL_OPTED_IN(HttpStatus.CONFLICT, "모든 참가자가 책임 알람에 동의해야 켤 수 있습니다."),
    ALARM_NOT_READY(HttpStatus.CONFLICT, "장소, 시간, 이동시간, 준비시간이 모두 정해져야 합니다."),
    MEETING_EXPIRED(HttpStatus.GONE, "기간이 지나 삭제된 약속입니다."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY, "외부 서비스 응답에 실패했습니다. 잠시 후 다시 시도해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
