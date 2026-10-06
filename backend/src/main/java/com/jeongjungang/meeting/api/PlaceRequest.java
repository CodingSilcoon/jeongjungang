package com.jeongjungang.meeting.api;

import com.jeongjungang.meeting.Meeting.PlaceInfo;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 확정 장소. lines는 지나는 호선(1~8호선만 지원하지만 여유를 둔다). */
public record PlaceRequest(
        @NotBlank @Size(max = 60) String name,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
        @Size(max = 10) List<@NotNull @Min(1) @Max(99) Integer> lines) {

    public PlaceInfo toInfo() {
        return new PlaceInfo(name.strip(), lat, lng, lines);
    }
}
