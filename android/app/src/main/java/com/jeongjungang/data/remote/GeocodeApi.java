package com.jeongjungang.data.remote;

import com.jeongjungang.domain.model.LatLng;
import java.util.List;

/**
 * 주소 검색과 역지오코딩. 서버가 있으면 {@link HttpGeocodeApi}, 없으면 {@link StationGeocodeApi}.
 * 둘 다 네트워크·파일 작업을 하므로 메인 스레드에서 부르지 않는다.
 */
public interface GeocodeApi {

    /** docs/API.md: query 2~100자, size 1~10. */
    int MIN_QUERY_LENGTH = 2;
    int MAX_QUERY_LENGTH = 100;
    int MAX_SIZE = 10;

    /** @return 결과가 없으면 빈 목록 */
    List<GeoPlace> search(String query, int size) throws ApiException;

    ReverseAddress reverse(LatLng location) throws ApiException;
}
