package com.jeongjungang.geocode.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jeongjungang.common.ApiException;
import com.jeongjungang.common.ErrorCode;
import com.jeongjungang.geocode.kakao.KakaoResponses.PlaceResult;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoLocalClientTest {

    private static final String BASE = "https://dapi.kakao.test";

    private static KakaoProperties props(String key) {
        return new KakaoProperties(key, BASE, Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    @Test
    void keywordNear_sendsKeyAndParams_andParsesSnakeCaseBody() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoLocalClient client = new KakaoLocalClient(builder.build(), props("test-key"));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v2/local/search/keyword.json")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "KakaoAK test-key"))
                .andExpect(queryParam("x", "127.052700"))
                .andExpect(queryParam("y", "37.566900"))
                .andExpect(queryParam("radius", "500"))
                .andExpect(queryParam("sort", "distance"))
                .andRespond(withSuccess("""
                        {"meta":{"is_end":false,"total_count":40},
                         "documents":[{"place_name":"투다리","category_name":"음식점 > 술집 > 호프","address_name":"서울 동대문구 답십리동 1",
                           "road_address_name":"서울 동대문구 천호대로 1","x":"127.0531","y":"37.5671","distance":"180",
                           "place_url":"http://place.map.kakao.com/1"}]}""", MediaType.APPLICATION_JSON));

        PlaceResult result = client.keywordNear("술집", 37.5669, 127.0527, 500, 1, 15);

        assertThat(result.meta().isEnd()).isFalse();
        assertThat(result.documents()).singleElement().satisfies(p -> {
            assertThat(p.placeName()).isEqualTo("투다리");
            assertThat(p.roadAddressName()).isEqualTo("서울 동대문구 천호대로 1");
            assertThat(p.distance()).isEqualTo("180");
        });
        server.verify();
    }

    @Test
    void kakaoFailure_becomesUpstreamError() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoLocalClient client = new KakaoLocalClient(builder.build(), props("test-key"));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v2/local/search/address.json")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.address("와우산로 94", 10))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UPSTREAM_ERROR));
    }

    @Test
    void missingKey_failsWithoutCallingKakao() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoLocalClient client = new KakaoLocalClient(builder.build(), props(" "));

        assertThatThrownBy(() -> client.coordToAddress(37.5, 127.0))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UPSTREAM_ERROR));
        server.verify();
    }
}
