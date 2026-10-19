package com.jeongjungang.push.api;

import com.jeongjungang.push.Device.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class DeviceRequests {

    private DeviceRequests() {
    }

    /** FCM 등록 토큰은 보통 150~200자다. 넉넉히 512자까지 받는다. */
    public record RegisterDevice(
            @NotBlank @Size(max = 512) String fcmToken,
            @NotNull Platform platform,
            @Size(max = 20) String appVersion) {
    }
}
