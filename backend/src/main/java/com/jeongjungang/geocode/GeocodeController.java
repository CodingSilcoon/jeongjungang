package com.jeongjungang.geocode;

import com.jeongjungang.common.ApiResponse;
import com.jeongjungang.geocode.GeoResponses.GeocodeResult;
import com.jeongjungang.geocode.GeoResponses.PlacePage;
import com.jeongjungang.geocode.GeoResponses.ReverseResult;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 주소 검색, 핀 주소, 주변 장소 (docs/API.md 5절, 6절). 모두 공개 API이고 IP당 30회/분으로 제한된다. */
@RestController
@RequestMapping("/api/v1")
public class GeocodeController {

    private final GeocodeService geocode;
    private final PlacesService places;

    public GeocodeController(GeocodeService geocode, PlacesService places) {
        this.geocode = geocode;
        this.places = places;
    }

    @GetMapping("/geocode")
    public ApiResponse<GeocodeResult> search(
            @RequestParam @NotBlank @Size(min = 2, max = 100, message = "검색어는 2~100자로 입력해 주세요.") String query,
            @RequestParam(defaultValue = "10") @Min(1) @Max(10) int size) {
        return ApiResponse.ok(geocode.search(query, size));
    }

    @GetMapping("/geocode/reverse")
    public ApiResponse<ReverseResult> reverse(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double lng) {
        return ApiResponse.ok(geocode.reverse(lat, lng));
    }

    @GetMapping("/places")
    public ApiResponse<PlacePage> places(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double lng,
            @RequestParam PlaceCategory category,
            @RequestParam(defaultValue = "500") @Min(100) @Max(1000) int radius,
            @RequestParam(defaultValue = "1") @Min(1) @Max(PlacesService.MAX_PAGE) int page) {
        return ApiResponse.ok(places.find(lat, lng, category, radius, page));
    }
}
