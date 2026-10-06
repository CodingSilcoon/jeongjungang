package com.jeongjungang.meeting;

import com.jeongjungang.config.AppProperties;
import com.jeongjungang.meeting.Meeting.PlaceInfo;
import com.jeongjungang.meeting.Participant.OriginInfo;
import com.jeongjungang.meeting.api.MeetingResponses.AccountabilityView;
import com.jeongjungang.meeting.api.MeetingResponses.MeetingView;
import com.jeongjungang.meeting.api.MeetingResponses.OriginView;
import com.jeongjungang.meeting.api.MeetingResponses.ParticipantSummary;
import com.jeongjungang.meeting.api.MeetingResponses.ParticipantView;
import com.jeongjungang.meeting.api.MeetingResponses.PlaceView;
import org.springframework.stereotype.Component;

/** 엔티티 → 응답 모양. 출발지 같은 개인 정보는 같은 약속 참가자용 응답에만 쓴다. */
@Component
class MeetingViews {

    private static final String INVITE_PATH = "/m/";

    private final AppProperties properties;

    MeetingViews(AppProperties properties) {
        this.properties = properties;
    }

    MeetingView meeting(Meeting m) {
        return new MeetingView(
                m.id(),
                m.inviteCode(),
                properties.publicBaseUrl() + INVITE_PATH + m.inviteCode(),
                m.title(),
                m.purpose(),
                m.meetAt(),
                m.status(),
                place(m.place()),
                m.expiresAt(),
                new AccountabilityView(m.accountabilityEnabled(), m.gracePeriodSec(), m.marginMinutes()));
    }

    static ParticipantSummary summary(Participant p) {
        return new ParticipantSummary(p.id(), p.nickname(), p.role());
    }

    static ParticipantView participant(Participant p) {
        return new ParticipantView(p.id(), p.nickname(), p.role(), origin(p.origin()), p.prepMinutes(), p.optedIn());
    }

    private static PlaceView place(PlaceInfo place) {
        return place == null ? null : new PlaceView(place.name(), place.lat(), place.lng(), place.lines());
    }

    private static OriginView origin(OriginInfo origin) {
        return origin == null ? null : new OriginView(origin.label(), origin.lat(), origin.lng());
    }
}
