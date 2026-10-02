package com.jeongjungang.data.repository;

import android.content.Context;
import com.jeongjungang.BuildConfig;
import com.jeongjungang.data.remote.GeocodeApi;
import com.jeongjungang.data.remote.HttpGeocodeApi;
import com.jeongjungang.data.remote.StationGeocodeApi;

/**
 * 주소 검색 구현을 고른다. local.properties의 {@code jeongjungang.apiBaseUrl}이 있으면 서버,
 * 없으면 번들 역 이름 검색({@link StationGeocodeApi})을 쓴다.
 */
public final class GeocodeRepository {

    private GeocodeRepository() {}

    public static boolean usesServer() {
        return !BuildConfig.API_BASE_URL.isEmpty();
    }

    public static GeocodeApi create(Context context) {
        if (usesServer()) {
            return new HttpGeocodeApi(BuildConfig.API_BASE_URL);
        }
        final TransitRepository transit = TransitRepository.getInstance(context);
        return new StationGeocodeApi(new StationGeocodeApi.DataProvider() {
            @Override
            public TransitData get() throws java.io.IOException {
                return transit.get();
            }
        });
    }
}
