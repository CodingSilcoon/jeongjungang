package com.jeongjungang.auth;

import com.jeongjungang.meeting.Role;
import java.util.UUID;

/**
 * 토큰으로 확인한 요청자. 컨트롤러 메서드에 이 타입 파라미터를 두면 인증이 필요한 API가 된다.
 * 토큰이 없거나 맞지 않으면 UNAUTHORIZED로 끝난다.
 */
public record Caller(UUID participantId, UUID meetingId, Role role) {

    public boolean isHost() {
        return role == Role.HOST;
    }
}
