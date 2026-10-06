package com.jeongjungang.meeting.api;

import com.jeongjungang.meeting.Participant.OriginInfo;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 출발지 입력. label 1~60자, 좌표 범위 확인 (docs/API.md 3절). */
public record OriginRequest(
        @NotBlank @Size(max = 60) String label,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng) {

    public OriginInfo toInfo() {
        return new OriginInfo(label.strip(), lat, lng);
    }
}
