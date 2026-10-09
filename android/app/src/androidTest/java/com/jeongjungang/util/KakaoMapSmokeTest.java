package com.jeongjungang.util;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.view.ViewGroup;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.jeongjungang.domain.model.LatLng;
import com.jeongjungang.ui.MainActivity;
import com.kakao.vectormap.KakaoMap;
import com.kakao.vectormap.KakaoMapReadyCallback;
import com.kakao.vectormap.MapLifeCycleCallback;
import com.kakao.vectormap.MapView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * 이 기기(에뮬레이터 포함)에서 카카오맵이 실제로 뜨는지 확인한다.
 * 키가 없으면 건너뛴다. 인증 실패(키 해시 미등록 등)면 onMapError로 와서 실패한다.
 * 실행: gradlew connectedDebugAndroidTest
 * 기기 화면이 켜져 있어야 한다(꺼져 있으면 지도를 그리지 않아 시간 초과). 에뮬레이터는 adb shell svc power stayon true
 */
@RunWith(AndroidJUnit4.class)
public class KakaoMapSmokeTest {

    private static final LatLng HYEHWA = new LatLng(37.5822, 127.0018);

    @Test
    public void mapBecomesReady() throws Exception {
        assumeTrue("local.properties에 카카오맵 키가 없음", KakaoMaps.isAvailable());

        final CountDownLatch ready = new CountDownLatch(1);
        final AtomicReference<Exception> error = new AtomicReference<Exception>();
        final AtomicReference<MapView> mapRef = new AtomicReference<MapView>();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                MapView map = new MapView(activity);
                ((ViewGroup) activity.findViewById(android.R.id.content)).addView(map,
                        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 600));
                mapRef.set(map);
                map.start(new MapLifeCycleCallback() {
                    @Override
                    public void onMapDestroy() {}

                    @Override
                    public void onMapError(Exception e) {
                        error.set(e);
                        ready.countDown();
                    }
                }, new KakaoMapReadyCallback() {
                    @Override
                    public void onMapReady(KakaoMap kakaoMap) {
                        ready.countDown();
                    }

                    @Override
                    public com.kakao.vectormap.LatLng getPosition() {
                        return KakaoMaps.toKakao(HYEHWA);
                    }
                });
            });

            assertTrue("30초 안에 지도가 준비되지 않음", ready.await(30, TimeUnit.SECONDS));
            assertNull("지도 오류: " + error.get(), error.get());

            scenario.onActivity(activity -> mapRef.get().finish());
        }
    }
}
