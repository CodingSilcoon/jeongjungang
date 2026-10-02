package com.jeongjungang.proxy;

import com.jeongjungang.common.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TravelTimeController {

    @GetMapping("/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.ok(Map.of("status", "ok"));
    }

    // TODO: POST /api/v1/travel-times — 후보역 x 참가자 소요시간 조회 (캐시 우선, 미스 시 ODsay)
}
