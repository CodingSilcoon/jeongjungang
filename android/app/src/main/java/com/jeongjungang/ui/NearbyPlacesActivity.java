package com.jeongjungang.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.jeongjungang.R;
import com.jeongjungang.data.remote.meeting.MeetingModels.NearbyPlace;
import com.jeongjungang.data.remote.meeting.MeetingModels.Place;
import com.jeongjungang.data.remote.meeting.MeetingModels.Purpose;
import com.jeongjungang.databinding.ActivityNearbyPlacesBinding;
import com.jeongjungang.domain.model.LatLng;
import com.jeongjungang.ui.map.MapPanel;
import com.jeongjungang.ui.meeting.PlacesViewModel;

import java.util.ArrayList;
import java.util.List;

/** 확정한 역 주변 500m의 실제 가게를 목적별로 검색한다. */
public class NearbyPlacesActivity extends AppCompatActivity {

    private ActivityNearbyPlacesBinding binding;
    private PlacesViewModel model;
    private LatLng center;
    private String stationName;
    private String category;
    private String selectedKey;
    private NearbyPlace selectedPlace;
    private boolean compact;
    private MapPanel mapPanel;

    /** 약속 확정 화면에서 서버의 실제 장소와 목적만 전달한다. */
    public static Intent createIntent(Context context, Place place, Purpose purpose) {
        return new Intent(context, NearbyPlacesActivity.class)
                .putExtra("stationName", place.name)
                .putExtra("lat", place.location.lat)
                .putExtra("lng", place.location.lng)
                .putExtra("category", purpose == null ? null : purpose.placeCategory);
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        EdgeToEdge.enable(this);
        binding = ActivityNearbyPlacesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        stationName = getIntent().getStringExtra("stationName");
        if (stationName == null || !getIntent().hasExtra("lat") || !getIntent().hasExtra("lng")) {
            finish();
            return;
        }

        center = new LatLng(getIntent().getDoubleExtra("lat", 0), getIntent().getDoubleExtra("lng", 0));
        mapPanel = MapPanel.attach(this, binding.mapArea, binding.mapLabel);
        category = saved == null ? getIntent().getStringExtra("category") : saved.getString("category");
        selectedKey = saved == null ? null : saved.getString("selectedKey");
        model = new ViewModelProvider(this).get(PlacesViewModel.class);

        binding.centerLabel.setText(getString(R.string.nearby_center, stationName));
        binding.backButton.setOnClickListener(v -> finish());
        binding.foodButton.setOnClickListener(v -> selectCategory("FOOD"));
        binding.cafeButton.setOnClickListener(v -> selectCategory("CAFE"));
        binding.barButton.setOnClickListener(v -> selectCategory("BAR"));
        binding.moreButton.setOnClickListener(v -> loadMoreOrRetry());
        binding.closeButton.setOnClickListener(v -> clearSelection());
        binding.openButton.setOnClickListener(v -> openPlace());

        applySafeArea();
        model.getState().observe(this, this::render);

        // 회전 때 진행 중인 조회를 재시작하지 않는다. 프로세스 복원 시에는 다시 조회한다.
        PlacesViewModel.State current = model.getState().getValue();
        if (category != null && (current == null || current.status == PlacesViewModel.State.Status.IDLE)) {
            model.search(center, category, 500);
        }
    }

    /** 시스템 영역을 피하고, 짧은 화면에서는 상세 정보도 목록과 함께 스크롤한다. */
    private void applySafeArea() {
        WindowCompat.getInsetsController(getWindow(), binding.getRoot()).setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), binding.getRoot()).setAppearanceLightNavigationBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });

        binding.content.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            boolean nextCompact = b - t < dp(600) || getResources().getConfiguration().fontScale > 1.3f;
            int height = nextCompact ? dp(80) : Math.min(dp(330), (b - t) * 35 / 100);
            binding.mapArea.setVisibility(b - t < dp(400) ? View.GONE : View.VISIBLE);
            binding.mapLabel.setPadding(dp(12), dp(nextCompact ? 20 : 32), dp(12), dp(4));
            if (binding.mapArea.getLayoutParams().height != height) {
                binding.mapArea.getLayoutParams().height = height;
                binding.mapArea.requestLayout();
            }
            if (compact != nextCompact) {
                compact = nextCompact;
                ((ViewGroup) binding.detailPanel.getParent()).removeView(binding.detailPanel);
                (compact ? binding.scrollContent : binding.content).addView(binding.detailPanel);
            }
        });
    }

    /** 분류를 바꾸면 이전 선택을 지우고 새 검색의 첫 페이지로 돌아간다. */
    private void selectCategory(String value) {
        if (value.equals(category)) return;
        category = value;
        selectedKey = null;
        selectedPlace = null;
        binding.listScroll.scrollTo(0, 0);
        model.search(center, category, 500);
    }

    private void loadMoreOrRetry() {
        PlacesViewModel.State state = model.getState().getValue();
        if (state == null || category == null) return;
        if (state.status == PlacesViewModel.State.Status.ERROR) model.search(center, category, 500);
        else model.loadMore();
    }

    /** 서버 결과만 표시한다. 다음 페이지 실패 시 기존 목록과 선택은 보존한다. */
    private void render(PlacesViewModel.State state) {
        if (state == null) return;
        int scroll = binding.listScroll.getScrollY();
        binding.foodButton.setSelected("FOOD".equals(category));
        binding.cafeButton.setSelected("CAFE".equals(category));
        binding.barButton.setSelected("BAR".equals(category));
        binding.listTitle.setText(category == null ? getString(R.string.nearby_choose)
                : getString(R.string.nearby_order, categoryLabel()));
        binding.placeList.removeAllViews();
        selectedPlace = null;

        for (NearbyPlace place : state.items) {
            addPlace(place);
            if (key(place).equals(selectedKey)) selectedPlace = place;
        }

        String message = "";
        if (category == null) message = getString(R.string.nearby_choose);
        else if (state.status == PlacesViewModel.State.Status.LOADING) message = getString(R.string.nearby_loading);
        else if (state.status == PlacesViewModel.State.Status.EMPTY) message = getString(R.string.nearby_empty);
        else if (state.errorMessage != null) message = state.errorMessage;
        binding.statusText.setText(message);
        binding.statusText.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);

        boolean retry = state.status == PlacesViewModel.State.Status.ERROR;
        binding.moreButton.setVisibility(category != null && (retry || state.hasMore) ? View.VISIBLE : View.GONE);
        binding.moreButton.setEnabled(!state.loadingMore);
        binding.moreButton.setText(state.loadingMore ? R.string.nearby_loading
                : retry || state.errorMessage != null ? R.string.nearby_retry : R.string.nearby_more);
        renderSelection();
        binding.listScroll.post(() -> binding.listScroll.scrollTo(0, scroll));
    }

    /** 가게명·분류·거리·주소를 읽을 수 있는 하나의 선택 버튼으로 묶는다. */
    private void addPlace(NearbyPlace place) {
        AppCompatButton button = new AppCompatButton(this);
        String heading = place.name + "   " + getString(R.string.nearby_distance, place.distanceMeters);
        SpannableString label = new SpannableString(heading + "\n" + text(place.category) + "\n" + text(place.address));
        label.setSpan(new StyleSpan(Typeface.BOLD), 0, heading.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        label.setSpan(new RelativeSizeSpan(1.2f), 0, heading.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        button.setText(label);
        button.setTextSize(13);
        button.setTextColor(getColor(R.color.start_ink));
        button.setAllCaps(false);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setPadding(dp(14), dp(14), dp(14), dp(14));
        button.setMinHeight(dp(80));
        button.setBackgroundResource(R.drawable.nearby_item_background);
        button.setSupportBackgroundTintList(null);
        button.setStateListAnimator(null);
        button.setTag(key(place));
        button.setOnClickListener(v -> {
            selectedKey = key(place);
            selectedPlace = place;
            renderSelection();
            if (compact) binding.listScroll.post(() -> binding.listScroll.scrollTo(0, binding.detailPanel.getTop()));
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        binding.placeList.addView(button, params);
    }

    /** 역과 불러온 가게를 지도에 찍는다. 가게 이름은 고른 가게만 보여서 지도가 글씨로 덮이지 않게 한다. */
    private void showOnMap() {
        List<MapPanel.Pin> pins = new ArrayList<>();
        pins.add(new MapPanel.Pin(stationName, center, MapPanel.Kind.SELECTED));
        PlacesViewModel.State state = model.getState().getValue();
        if (state != null) {
            for (NearbyPlace place : state.items) {
                boolean chosen = key(place).equals(selectedKey);
                pins.add(new MapPanel.Pin(chosen ? place.name : null, place.location,
                        chosen ? MapPanel.Kind.SELECTED : MapPanel.Kind.CANDIDATE));
            }
        }
        mapPanel.show(pins);
    }

    /** 상세 카드와 목록의 선택 상태를 함께 갱신한다. */
    private void renderSelection() {
        for (int i = 0; i < binding.placeList.getChildCount(); i++) {
            AppCompatButton item = (AppCompatButton) binding.placeList.getChildAt(i);
            boolean chosen = item.getTag().equals(selectedKey);
            item.setSelected(chosen);
            item.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0,
                    chosen ? R.drawable.ic_point_check : 0, 0);
            item.setCompoundDrawablePadding(dp(8));
        }
        binding.detailPanel.setVisibility(selectedPlace == null ? View.GONE : View.VISIBLE);
        binding.mapLabel.setText(stationName + "\n\n" + getString(R.string.recommend_map_pending));
        showOnMap();
        binding.linkStatus.setVisibility(View.GONE);
        if (selectedPlace == null) return;

        binding.detailName.setText(selectedPlace.name);
        binding.detailAddress.setText(getString(R.string.nearby_detail, text(selectedPlace.category),
                selectedPlace.distanceMeters, text(selectedPlace.address)));
        binding.mapLabel.setText(stationName + "\n" + getString(R.string.recommend_map_pending)
                + "\n" + getString(R.string.nearby_selected, selectedPlace.name));
        boolean hasUrl = placeUri(selectedPlace) != null;
        binding.openButton.setEnabled(hasUrl);
        binding.openButton.setAlpha(hasUrl ? 1f : .45f);
        if (!hasUrl) showLinkMessage(R.string.nearby_no_link);
    }

    private void clearSelection() {
        String previous = selectedKey;
        selectedKey = null;
        selectedPlace = null;
        renderSelection();
        for (int i = 0; i < binding.placeList.getChildCount(); i++) {
            View item = binding.placeList.getChildAt(i);
            if (item.getTag().equals(previous)) item.requestFocus();
        }
    }

    /** API가 반환한 카카오 장소 웹 주소를 그대로 연다(HTTP 응답도 지원). */
    private Uri placeUri(NearbyPlace place) {
        if (place == null || place.placeUrl == null) return null;
        Uri uri = Uri.parse(place.placeUrl);
        return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                && "place.map.kakao.com".equalsIgnoreCase(uri.getHost())
                ? uri : null;
    }

    private void openPlace() {
        Uri uri = placeUri(selectedPlace);
        if (uri == null) { showLinkMessage(R.string.nearby_no_link); return; }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            showLinkMessage(R.string.nearby_open_failed);
        }
    }

    private void showLinkMessage(int resource) {
        binding.linkStatus.setText(resource);
        binding.linkStatus.setVisibility(View.VISIBLE);
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String categoryLabel() {
        return getString("FOOD".equals(category) ? R.string.nearby_food
                : "CAFE".equals(category) ? R.string.nearby_cafe : R.string.nearby_bar);
    }

    private String key(NearbyPlace place) {
        return place.name + "|" + place.location.lat + "|" + place.location.lng;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle saved) {
        super.onSaveInstanceState(saved);
        saved.putString("category", category);
        saved.putString("selectedKey", selectedKey);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
