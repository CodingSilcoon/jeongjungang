package com.jeongjungang.geocode;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jeongjungang.IntegrationTestSupport;
import com.jeongjungang.common.ApiException;
import com.jeongjungang.common.ErrorCode;
import com.jeongjungang.geocode.kakao.KakaoLocalClient;
import com.jeongjungang.geocode.kakao.KakaoResponses.Address;
import com.jeongjungang.geocode.kakao.KakaoResponses.AddressName;
import com.jeongjungang.geocode.kakao.KakaoResponses.AddressResult;
import com.jeongjungang.geocode.kakao.KakaoResponses.CoordAddress;
import com.jeongjungang.geocode.kakao.KakaoResponses.CoordAddressResult;
import com.jeongjungang.geocode.kakao.KakaoResponses.Meta;
import com.jeongjungang.geocode.kakao.KakaoResponses.Place;
import com.jeongjungang.geocode.kakao.KakaoResponses.PlaceResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 주소 검색·핀 주소·주변 장소를 가짜 카카오와 실제 Redis 캐시로 확인한다. */
@AutoConfigureMockMvc
class GeocodeApiIntegrationTest extends IntegrationTestSupport {

    private static final Place HONGDAE = new Place("홍대입구역 2호선", "교통,수송 > 지하철,전철 > 수도권2호선",
            "서울 마포구 동교동 165", "서울 마포구 양화로 160", "126.9245", "37.5572", null, "http://place.map.kakao.com/1");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private KakaoLocalClient kakao;

    @Test
    void search_putsAddressesBeforePlaces_andCachesFor24h() throws Exception {
        when(kakao.address("와우산로 94", 10)).thenReturn(new AddressResult(List.of(
                new Address("서울 마포구 와우산로 94", "126.9227", "37.5489",
                        new AddressName("서울 마포구 상수동 331-5"), new AddressName("서울 마포구 와우산로 94")))));
        when(kakao.keyword("와우산로 94", 10)).thenReturn(new PlaceResult(new Meta(true), List.of(HONGDAE)));

        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/v1/geocode").param("query", "  와우산로   94 "))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items", hasSize(2)))
                    .andExpect(jsonPath("$.data.items[0].type").value("ADDRESS"))
                    .andExpect(jsonPath("$.data.items[0].name").value("서울 마포구 와우산로 94"))
                    .andExpect(jsonPath("$.data.items[0].address").value("서울 마포구 상수동 331-5"))
                    .andExpect(jsonPath("$.data.items[0].lat").value(37.5489))
                    .andExpect(jsonPath("$.data.items[1].type").value("PLACE"))
                    .andExpect(jsonPath("$.data.items[1].address").value("서울 마포구 양화로 160"));
        }
        verify(kakao, times(1)).address("와우산로 94", 10);
    }

    @Test
    void search_noResults_isEmptyList_notNotFound() throws Exception {
        when(kakao.address(anyString(), anyInt())).thenReturn(new AddressResult(List.of()));
        when(kakao.keyword(anyString(), anyInt())).thenReturn(new PlaceResult(new Meta(true), List.of()));

        mvc.perform(get("/api/v1/geocode").param("query", "없는곳xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    void search_validatesQueryAndSize() throws Exception {
        mvc.perform(get("/api/v1/geocode").param("query", "홍"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/geocode").param("query", "   "))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/geocode").param("query", "홍대").param("size", "11"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/geocode")).andExpect(status().isBadRequest());
    }

    @Test
    void kakaoFailure_is502WithoutInternals() throws Exception {
        when(kakao.address(anyString(), anyInt()))
                .thenThrow(new ApiException(ErrorCode.UPSTREAM_ERROR, "카카오 address 호출 실패: 401 KakaoAK secret", null));

        mvc.perform(get("/api/v1/geocode").param("query", "홍대입구"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("UPSTREAM_ERROR"))
                .andExpect(content().string(not(containsString("KakaoAK"))));
    }

    @Test
    void reverse_returnsBothAddresses_orNullsWhenUnknown() throws Exception {
        when(kakao.coordToAddress(37.5489, 126.9227)).thenReturn(new CoordAddressResult(List.of(
                new CoordAddress(new AddressName("서울 마포구 상수동 331-5"), new AddressName("서울 마포구 와우산로 94")))));
        when(kakao.coordToAddress(10.0, 10.0)).thenReturn(new CoordAddressResult(List.of()));

        mvc.perform(get("/api/v1/geocode/reverse").param("lat", "37.5489").param("lng", "126.9227"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address").value("서울 마포구 상수동 331-5"))
                .andExpect(jsonPath("$.data.roadAddress").value("서울 마포구 와우산로 94"));
        mvc.perform(get("/api/v1/geocode/reverse").param("lat", "10").param("lng", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address").doesNotExist())
                .andExpect(jsonPath("$.data.roadAddress").doesNotExist());
        mvc.perform(get("/api/v1/geocode/reverse").param("lat", "91").param("lng", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void places_food_usesCategorySearch_andMapsFields() throws Exception {
        Place restaurant = new Place("답십리국밥", "음식점 > 한식 > 국밥", "서울 동대문구 답십리동 1",
                "", "127.0531", "37.5671", "180", "http://place.map.kakao.com/2");
        when(kakao.category(eq("FD6"), anyDouble(), anyDouble(), eq(500), eq(1), eq(15)))
                .thenReturn(new PlaceResult(new Meta(false), List.of(restaurant)));

        mvc.perform(get("/api/v1/places").param("lat", "37.5669").param("lng", "127.0527").param("category", "FOOD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("답십리국밥"))
                .andExpect(jsonPath("$.data.items[0].category").value("국밥"))
                .andExpect(jsonPath("$.data.items[0].address").value("서울 동대문구 답십리동 1"))
                .andExpect(jsonPath("$.data.items[0].distanceMeters").value(180))
                .andExpect(jsonPath("$.data.items[0].placeUrl").value("http://place.map.kakao.com/2"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    void places_bar_usesKeywordSearch() throws Exception {
        when(kakao.keywordNear(eq("술집"), anyDouble(), anyDouble(), eq(300), eq(2), eq(15)))
                .thenReturn(new PlaceResult(new Meta(true), List.of()));

        mvc.perform(get("/api/v1/places").param("lat", "37.5669").param("lng", "127.0527")
                        .param("category", "BAR").param("radius", "300").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void places_validatesParams() throws Exception {
        mvc.perform(get("/api/v1/places").param("lat", "37.5").param("lng", "127.0").param("category", "PARK"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/places").param("lat", "37.5").param("lng", "127.0").param("category", "CAFE")
                        .param("radius", "50"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/places").param("lat", "37.5").param("lng", "127.0"))
                .andExpect(status().isBadRequest());
    }
}
