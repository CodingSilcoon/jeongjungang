package com.jeongjungang.ui.map;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import com.jeongjungang.R;
import com.jeongjungang.domain.model.LatLng;
import com.jeongjungang.util.KakaoMaps;
import com.kakao.vectormap.GestureType;
import com.kakao.vectormap.KakaoMap;
import com.kakao.vectormap.KakaoMapReadyCallback;
import com.kakao.vectormap.MapLifeCycleCallback;
import com.kakao.vectormap.MapView;
import com.kakao.vectormap.camera.CameraUpdateFactory;
import com.kakao.vectormap.label.LabelLayer;
import com.kakao.vectormap.label.LabelOptions;
import com.kakao.vectormap.label.LabelStyle;
import com.kakao.vectormap.label.LabelStyles;
import com.kakao.vectormap.label.LabelTextBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 화면의 지도 칸(FrameLayout)에 카카오맵을 깔고 핀을 찍는다.
 * 지도를 못 쓰면(키 없음, ARM을 못 돌리는 기기, 인증 실패) 아무것도 바꾸지 않아서 칸에 있던 안내 문구가 그대로 보인다.
 * 지도가 준비되면 {@code placeholders}로 넘긴 안내 문구를 숨긴다.
 *
 * <p>Activity 생명주기에 맞춰 resume/pause/finish를 알아서 부른다(공식 문서: 안 부르면 크래시).
 */
public final class MapPanel implements DefaultLifecycleObserver {

    public enum Kind {
        /** 참가자 출발지 */
        ORIGIN,
        /** 후보 역, 주변 가게 */
        CANDIDATE,
        /** 선택한 역·가게 */
        SELECTED
    }

    public static final class Pin {
        final String label;
        final LatLng at;
        final Kind kind;

        public Pin(String label, LatLng at, Kind kind) {
            this.label = label;
            this.at = at;
            this.kind = kind;
        }
    }

    private static final String TAG = "MapPanel";
    /** 핀 하나만 있을 때 확대 수준 (카카오맵 기준 15~16이 역 주변 골목 정도) */
    private static final int SINGLE_PIN_ZOOM = 16;

    private final Context context;
    private final View[] placeholders;
    private MapView mapView;
    private KakaoMap map;
    private List<Pin> pins = Collections.emptyList();
    /** 다음 render에서 카메라를 핀에 맞출지. 지도가 준비될 때와 핀 위치가 바뀔 때 true */
    private boolean recenter = true;

    private MapPanel(Context context, View[] placeholders) {
        this.context = context;
        this.placeholders = placeholders;
    }

    /**
     * 지도 칸에 지도를 붙인다. 지도를 못 쓰는 환경이면 칸은 그대로 두고 빈 패널을 돌려준다(show를 불러도 무시).
     *
     * @param container    지도를 깔 칸. 맨 아래(뒤)에 지도가 들어가서 칸 안의 다른 글씨는 지도 위에 남는다
     * @param placeholders 지도가 뜨면 숨길 안내 문구
     */
    public static MapPanel attach(ComponentActivity activity, FrameLayout container, View... placeholders) {
        MapPanel panel = new MapPanel(activity, placeholders);
        if (KakaoMaps.isAvailable()) {
            panel.start(container);
            activity.getLifecycle().addObserver(panel);
        }
        return panel;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void start(FrameLayout container) {
        mapView = new MapView(context);
        // 스크롤 화면 안에 있어서, 지도를 만지는 동안은 바깥 스크롤이 손가락을 가로채지 않게 한다
        mapView.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            v.getParent().requestDisallowInterceptTouchEvent(
                    action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL);
            return false;
        });
        container.addView(mapView, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mapView.start(new MapLifeCycleCallback() {
            @Override
            public void onMapDestroy() {
                map = null;
            }

            @Override
            public void onMapError(Exception error) {
                Log.w(TAG, "지도를 띄우지 못해 안내 문구를 그대로 둡니다", error);
                map = null;
                mapView.setVisibility(View.GONE);
                setPlaceholdersVisible(true);
            }
        }, new KakaoMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull KakaoMap kakaoMap) {
                map = kakaoMap;
                kakaoMap.setGestureEnable(GestureType.Rotate, false);
                kakaoMap.setGestureEnable(GestureType.Tilt, false);
                kakaoMap.setGestureEnable(GestureType.RotateZoom, false);
                setPlaceholdersVisible(false);
                recenter = true;
                render();
            }
        });
    }

    /**
     * 핀을 바꾼다. 지도가 아직 준비 중이면 준비되는 대로 찍는다.
     * 같은 핀이면 아무것도 안 한다(폴링으로 화면을 자주 다시 그려도 사용자가 옮긴 지도가 튀지 않게).
     * 카메라는 핀 위치가 바뀔 때만 모든 핀이 보이게 다시 맞춘다.
     */
    public void show(List<Pin> next) {
        String nextPins = signature(next, true);
        if (nextPins.equals(signature(pins, true))) {
            return;
        }
        recenter = !signature(next, false).equals(signature(pins, false));
        pins = new ArrayList<Pin>(next);
        render();
    }

    /** 핀 목록 비교용 문자열. withLooks가 false면 위치만 본다. */
    private static String signature(List<Pin> list, boolean withLooks) {
        StringBuilder sb = new StringBuilder();
        for (Pin p : list) {
            sb.append(p.at.lat).append(',').append(p.at.lng);
            if (withLooks) {
                sb.append(',').append(p.kind).append(',').append(p.label);
            }
            sb.append(';');
        }
        return sb.toString();
    }

    /** 지도가 실제로 떠 있으면 true. 안내 문구를 바꿀지 판단할 때 쓴다. */
    public boolean isShowing() {
        return map != null;
    }

    private void render() {
        if (map == null) {
            return;
        }
        LabelLayer layer = map.getLabelManager().getLayer();
        if (layer == null) {
            return;
        }
        layer.removeAll();
        if (pins.isEmpty()) {
            return;
        }
        com.kakao.vectormap.LatLng[] points = new com.kakao.vectormap.LatLng[pins.size()];
        for (int i = 0; i < pins.size(); i++) {
            Pin pin = pins.get(i);
            points[i] = KakaoMaps.toKakao(pin.at);
            LabelOptions options = LabelOptions.from(points[i])
                    .setStyles(LabelStyles.from(styleFor(pin.kind)))
                    // 선택한 핀이 다른 핀 위에 그려지게 한다
                    .setRank(pin.kind == Kind.SELECTED ? 2 : pin.kind == Kind.CANDIDATE ? 1 : 0);
            if (pin.label != null && !pin.label.isEmpty()) {
                options.setTexts(new LabelTextBuilder().setTexts(pin.label));
            }
            layer.addLabel(options);
        }
        if (!recenter) {
            return;
        }
        recenter = false;
        if (points.length == 1) {
            map.moveCamera(CameraUpdateFactory.newCenterPosition(points[0], SINGLE_PIN_ZOOM));
        } else {
            map.moveCamera(CameraUpdateFactory.fitMapPoints(points, dp(48)));
        }
    }

    private LabelStyle styleFor(Kind kind) {
        int ink = context.getColor(R.color.start_ink);
        int fill;
        int size;
        switch (kind) {
            case SELECTED:
                fill = context.getColor(R.color.start_accent);
                size = dp(26);
                break;
            case CANDIDATE:
                fill = context.getColor(R.color.white);
                size = dp(18);
                break;
            default:
                fill = context.getColor(R.color.start_point);
                size = dp(14);
                break;
        }
        return LabelStyle.from(dot(size, fill, ink))
                .setApplyDpScale(false)
                .setAnchorPoint(0.5f, 0.5f)
                .setTextStyles(sp(kind == Kind.SELECTED ? 14 : 12), ink);
    }

    /** 테두리 있는 동그라미 핀. 벡터 리소스는 지도 SDK가 못 읽어서 비트맵으로 그린다. */
    private Bitmap dot(int size, int fill, int border) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float stroke = Math.max(2f, size / 8f);
        float r = size / 2f;
        paint.setColor(fill);
        canvas.drawCircle(r, r, r - stroke / 2f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setColor(border);
        canvas.drawCircle(r, r, r - stroke / 2f, paint);
        return bitmap;
    }

    private void setPlaceholdersVisible(boolean visible) {
        for (View v : placeholders) {
            v.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private int sp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().scaledDensity);
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        mapView.resume();
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        mapView.pause();
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        owner.getLifecycle().removeObserver(this);
        mapView.finish();
    }
}
