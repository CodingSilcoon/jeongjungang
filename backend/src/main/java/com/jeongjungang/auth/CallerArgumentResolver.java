package com.jeongjungang.auth;

import com.jeongjungang.common.ApiException;
import com.jeongjungang.common.ErrorCode;
import com.jeongjungang.meeting.ParticipantRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Caller 파라미터를 Bearer 토큰으로 채운다. 매 요청 DB에서 다시 읽으므로 방장이 바뀌면 바로 반영된다. */
@Component
public class CallerArgumentResolver implements HandlerMethodArgumentResolver {

    private final ParticipantRepository participants;

    public CallerArgumentResolver(ParticipantRepository participants) {
        this.participants = participants;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Caller.class.equals(parameter.getParameterType());
    }

    @Override
    public Caller resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return BearerToken.from(request)
                .map(ParticipantTokens::hash)
                .flatMap(participants::findByTokenHash)
                .map(p -> new Caller(p.id(), p.meetingId(), p.role()))
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }
}
