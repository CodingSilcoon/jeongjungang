package com.jeongjungang.alarm.api;

import com.jeongjungang.alarm.AccountabilityService;
import com.jeongjungang.alarm.AlarmService;
import com.jeongjungang.alarm.api.AlarmRequests.DismissAlarm;
import com.jeongjungang.alarm.api.AlarmRequests.EnableAccountability;
import com.jeongjungang.alarm.api.AlarmRequests.ReportTravel;
import com.jeongjungang.alarm.api.AlarmResponses.DismissResult;
import com.jeongjungang.alarm.api.AlarmResponses.MyAlarm;
import com.jeongjungang.auth.Caller;
import com.jeongjungang.common.ApiResponse;
import com.jeongjungang.meeting.api.MeetingResponses.AccountabilityView;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 책임 알람 (docs/API.md 7절). 모두 참가자 토큰이 필요하다. */
@RestController
@RequestMapping("/api/v1")
public class AlarmController {

    private final AccountabilityService accountability;
    private final AlarmService alarms;

    public AlarmController(AccountabilityService accountability, AlarmService alarms) {
        this.accountability = accountability;
        this.alarms = alarms;
    }

    @PutMapping("/participants/{participantId}/travel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reportTravel(Caller caller, @PathVariable UUID participantId,
                             @Valid @RequestBody ReportTravel request) {
        accountability.reportTravel(caller, participantId, request);
    }

    @PostMapping("/meetings/{meetingId}/accountability")
    public ApiResponse<AccountabilityView> enable(Caller caller, @PathVariable UUID meetingId,
                                                  @Valid @RequestBody(required = false) EnableAccountability request) {
        return ApiResponse.ok(accountability.enable(caller, meetingId,
                request == null ? EnableAccountability.DEFAULTS : request));
    }

    @DeleteMapping("/meetings/{meetingId}/accountability")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(Caller caller, @PathVariable UUID meetingId) {
        accountability.disable(caller, meetingId);
    }

    @GetMapping("/meetings/{meetingId}/alarms")
    public ResponseEntity<ApiResponse<MyAlarm>> myAlarm(Caller caller, @PathVariable UUID meetingId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(alarms.myAlarm(caller, meetingId)));
    }

    @PostMapping("/alarms/{alarmId}/ringing")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ringing(Caller caller, @PathVariable UUID alarmId) {
        alarms.ringing(caller, alarmId);
    }

    @PostMapping("/alarms/{alarmId}/dismiss")
    public ApiResponse<DismissResult> dismiss(Caller caller, @PathVariable UUID alarmId,
                                              @Valid @RequestBody(required = false) DismissAlarm request) {
        return ApiResponse.ok(alarms.dismiss(caller, alarmId, request == null ? DismissAlarm.EMPTY : request));
    }
}
