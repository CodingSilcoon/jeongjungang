package com.jeongjungang.util;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import com.jeongjungang.BuildConfig;
import com.jeongjungang.domain.model.LatLng;
import com.kakao.vectormap.KakaoMapSdk;

/**
 * 카카오맵 SDK v2 초기화와 사용 가능 여부.
 * 키(local.properties의 jeongjungang.kakaoNativeAppKey)가 없거나 초기화에 실패하면 지도만 끄고 앱은 그대로 쓴다.
 * 화면은 {@link #isAvailable()}이 false면 MapView를 만들지 말고 지도 없는 화면(목록·주소)을 보여 준다.
 *
 * <p>MapView 쓰는 법(공식 문서 quickstart): {@code mapView.start(MapLifeCycleCallback, KakaoMapReadyCallback)},
 * Activity의 onResume/onPause에서 {@code mapView.resume()}/{@code mapView.pause()}를 꼭 부른다
 * (안 부르면 알 수 없는 크래시가 난다고 문서에 적혀 있다).
 */
public final class KakaoMaps {

    private static final String TAG = "KakaoMaps";

    private static volatile boolean available;

    private KakaoMaps() {}

    /** Application.onCreate에서 한 번 부른다. 여러 번 불러도 된다. */
    public static synchronized void init(Context context) {
        if (available) {
            return;
        }
        String key = BuildConfig.KAKAO_NATIVE_APP_KEY;
        if (key == null || key.isEmpty()) {
            Log.i(TAG, "카카오맵 키가 없어 지도를 끕니다 (local.properties의 jeongjungang.kakaoNativeAppKey)");
            return;
        }
        if (!supportsArm()) {
            // SDK가 ARM 라이브러리만 들고 있다. ARM 번역이 없는 x86 기기에서는 지도를 만들면 죽는다.
            Log.w(TAG, "ARM을 실행할 수 없는 기기라 지도를 끕니다: " + String.join(",", Build.SUPPORTED_ABIS));
            return;
        }
        try {
            KakaoMapSdk.init(context.getApplicationContext(), key);
            available = KakaoMapSdk.isInitialized();
        } catch (RuntimeException | LinkageError e) {
            Log.w(TAG, "카카오맵 초기화 실패, 지도를 끕니다", e);
            available = false;
        }
    }

    /** 지도를 띄워도 되면 true. */
    public static boolean isAvailable() {
        return available;
    }

    /** 앱 좌표를 카카오맵 좌표로. 패키지는 다르고 이름이 같아서 전체 이름으로 쓴다. */
    public static com.kakao.vectormap.LatLng toKakao(LatLng p) {
        return com.kakao.vectormap.LatLng.from(p.lat, p.lng);
    }

    private static boolean supportsArm() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi) || "armeabi-v7a".equals(abi)) {
                return true;
            }
        }
        return false;
    }
}
