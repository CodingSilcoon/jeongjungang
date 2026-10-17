package com.jeongjungang.push.api;

import com.jeongjungang.auth.Caller;
import com.jeongjungang.push.DeviceService;
import com.jeongjungang.push.api.DeviceRequests.RegisterDevice;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 푸시 받을 기기 등록 (docs/API.md 7절). */
@RestController
public class DeviceController {

    private final DeviceService devices;

    public DeviceController(DeviceService devices) {
        this.devices = devices;
    }

    @PostMapping("/api/v1/devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(Caller caller, @Valid @RequestBody RegisterDevice request) {
        devices.register(caller, request);
    }
}
