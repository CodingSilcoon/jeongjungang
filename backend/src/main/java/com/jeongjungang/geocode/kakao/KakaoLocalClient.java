package com.jeongjungang.geocode.kakao;

import com.jeongjungang.common.ApiException;
import com.jeongjungang.common.ErrorCode;
import com.jeongjungang.geocode.kakao.KakaoResponses.AddressResult;
import com.jeongjungang.geocode.kakao.KakaoResponses.CoordAddressResult;
import com.jeongjungang.geocode.kakao.KakaoResponses.PlaceResult;
import java.net.URI;
import java.util.Locale;
import java.util.function.Function;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

/**
 * 카카오 로컬 REST API 호출 (https://developers.kakao.com/docs/latest/ko/local/dev-guide).
 * 실패는 모두 UPSTREAM_ERROR(502)로 바꾸고, 자세한 원인은 로그에만 남긴다(응답에 키·외부 응답을 싣지 않음).
 */
public class KakaoLocalClient {

    private static final String AUTH_SCHEME = "KakaoAK ";

    private final RestClient http;
    private final KakaoProperties properties;

    public KakaoLocalClient(RestClient http, KakaoProperties properties) {
        this.http = http;
        this.properties = properties;
    }

    /** 장소 이름 검색. 중심 좌표 없이 전국에서 찾는다. */
    public PlaceResult keyword(String query, int size) {
        return get("keyword", PlaceResult.class, uri -> uri.path("/v2/local/search/keyword.json")
                .queryParam("query", query)
                .queryParam("size", size)
                .build());
    }

    /** 중심 좌표 반경 안에서 키워드로 찾는다. 가까운 순. */
    public PlaceResult keywordNear(String query, double lat, double lng, int radius, int page, int size) {
        return get("keywordNear", PlaceResult.class, uri -> uri.path("/v2/local/search/keyword.json")
                .queryParam("query", query)
                .queryParam("x", coord(lng))
                .queryParam("y", coord(lat))
                .queryParam("radius", radius)
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParam("sort", "distance")
                .build());
    }

    /** 카테고리 그룹 코드(FD6 음식점, CE7 카페 등)로 반경 안을 찾는다. 가까운 순. */
    public PlaceResult category(String groupCode, double lat, double lng, int radius, int page, int size) {
        return get("category", PlaceResult.class, uri -> uri.path("/v2/local/search/category.json")
                .queryParam("category_group_code", groupCode)
                .queryParam("x", coord(lng))
                .queryParam("y", coord(lat))
                .queryParam("radius", radius)
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParam("sort", "distance")
                .build());
    }

    public AddressResult address(String query, int size) {
        return get("address", AddressResult.class, uri -> uri.path("/v2/local/search/address.json")
                .queryParam("query", query)
                .queryParam("size", size)
                .build());
    }

    public CoordAddressResult coordToAddress(double lat, double lng) {
        return get("coord2address", CoordAddressResult.class, uri -> uri.path("/v2/local/geo/coord2address.json")
                .queryParam("x", coord(lng))
                .queryParam("y", coord(lat))
                .build());
    }

    private <T> T get(String operation, Class<T> type, Function<UriBuilder, URI> uri) {
        if (!properties.hasKey()) {
            throw new ApiException(ErrorCode.UPSTREAM_ERROR, "KAKAO_REST_API_KEY가 설정되지 않았습니다", null);
        }
        try {
            T body = http.get()
                    .uri(uri)
                    .header("Authorization", AUTH_SCHEME + properties.restApiKey())
                    .retrieve()
                    .body(type);
            if (body == null) {
                throw new ApiException(ErrorCode.UPSTREAM_ERROR, "카카오 " + operation + " 응답 본문이 비었습니다", null);
            }
            return body;
        } catch (RestClientException e) {
            throw new ApiException(ErrorCode.UPSTREAM_ERROR, "카카오 " + operation + " 호출 실패: " + e.getMessage(), e);
        }
    }

    private static String coord(double value) {
        return String.format(Locale.US, "%.6f", value);
    }
}
