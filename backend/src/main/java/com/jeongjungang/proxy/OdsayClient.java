package com.jeongjungang.proxy;

import com.jeongjungang.common.UpstreamApiException;
import com.jeongjungang.config.ExternalApiProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** ODsay 대중교통 경로 API 클라이언트. API 키는 서버에서만 보유. */
@Component
public class OdsayClient {

    private final RestClient client;
    private final String apiKey;

    public OdsayClient(@Qualifier("odsayRestClient") RestClient client,
                       ExternalApiProperties props) {
        this.client = client;
        this.apiKey = props.odsay().apiKey();
    }

    /** 두 좌표 간 대중교통 소요시간(분). TODO: searchPubTransPathT 응답 파싱 */
    public int fetchMinutes(double fromLng, double fromLat, double toLng, double toLat) {
        throw new UpstreamApiException("ODsay 연동 미구현");
    }
}
