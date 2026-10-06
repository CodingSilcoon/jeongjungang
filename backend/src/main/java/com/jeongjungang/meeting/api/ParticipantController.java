package com.jeongjungang.meeting.api;

import com.jeongjungang.auth.Caller;
import com.jeongjungang.meeting.ParticipantService;
import com.jeongjungang.meeting.api.MeetingRequests.UpdateParticipant;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 참가자 정보 수정, 나가기·내보내기 (docs/API.md 6절). */
@RestController
@RequestMapping("/api/v1/participants")
public class ParticipantController {

    private final ParticipantService participants;

    public ParticipantController(ParticipantService participants) {
        this.participants = participants;
    }

    @PatchMapping("/{participantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(Caller caller, @PathVariable UUID participantId,
                       @Valid @RequestBody UpdateParticipant request) {
        participants.update(caller, participantId, request);
    }

    @DeleteMapping("/{participantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Caller caller, @PathVariable UUID participantId) {
        participants.remove(caller, participantId);
    }
}
