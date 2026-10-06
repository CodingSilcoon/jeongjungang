package com.jeongjungang.meeting.api;

import com.jeongjungang.auth.Caller;
import com.jeongjungang.common.ApiResponse;
import com.jeongjungang.meeting.MeetingService;
import com.jeongjungang.meeting.api.MeetingRequests.CreateMeeting;
import com.jeongjungang.meeting.api.MeetingRequests.JoinMeeting;
import com.jeongjungang.meeting.api.MeetingRequests.UpdateMeeting;
import com.jeongjungang.meeting.api.MeetingResponses.Created;
import com.jeongjungang.meeting.api.MeetingResponses.InvitePreview;
import com.jeongjungang.meeting.api.MeetingResponses.Joined;
import com.jeongjungang.meeting.api.MeetingResponses.Snapshot;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/** 약속과 초대 (docs/API.md 6절). Caller 파라미터가 있는 메서드는 참가자 토큰이 필요하다. */
@RestController
@RequestMapping("/api/v1/meetings")
public class MeetingController {

    private static final String ETAG_PREFIX = "v";

    private final MeetingService meetings;

    public MeetingController(MeetingService meetings) {
        this.meetings = meetings;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Created> create(@Valid @RequestBody CreateMeeting request) {
        return ApiResponse.ok(meetings.create(request));
    }

    @GetMapping("/by-code/{inviteCode}")
    public ApiResponse<InvitePreview> preview(@PathVariable String inviteCode) {
        return ApiResponse.ok(meetings.preview(inviteCode));
    }

    @PostMapping("/by-code/{inviteCode}/participants")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Joined> join(@PathVariable String inviteCode, @Valid @RequestBody JoinMeeting request) {
        return ApiResponse.ok(meetings.join(inviteCode, request));
    }

    /** 앱이 3~5초마다 부른다. 바뀐 게 없으면 If-None-Match에 304로 답한다. */
    @GetMapping("/{meetingId}")
    public ResponseEntity<ApiResponse<Snapshot>> get(Caller caller, @PathVariable UUID meetingId, WebRequest request) {
        Snapshot snapshot = meetings.snapshot(caller, meetingId);
        if (request.checkNotModified(ETAG_PREFIX + snapshot.version())) {
            return null;
        }
        // 참가자 출발지가 들어 있어서 공유 캐시(프록시)에 남지 않게 한다
        return ResponseEntity.ok().cacheControl(CacheControl.noCache().cachePrivate()).body(ApiResponse.ok(snapshot));
    }

    @PatchMapping("/{meetingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(Caller caller, @PathVariable UUID meetingId, @Valid @RequestBody UpdateMeeting request) {
        meetings.update(caller, meetingId, request);
    }

    @DeleteMapping("/{meetingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(Caller caller, @PathVariable UUID meetingId) {
        meetings.cancel(caller, meetingId);
    }
}
