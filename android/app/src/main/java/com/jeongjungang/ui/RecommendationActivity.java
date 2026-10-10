package com.jeongjungang.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.jeongjungang.R;
import com.jeongjungang.data.remote.GeoPlace;
import com.jeongjungang.databinding.ActivityRecommendationBinding;
import com.jeongjungang.domain.model.LatLng;
import com.jeongjungang.domain.recommend.Criterion;
import com.jeongjungang.domain.recommend.Participant;
import com.jeongjungang.domain.recommend.PersonTrip;
import com.jeongjungang.domain.recommend.Recommendation;
import com.jeongjungang.ui.recommend.RecommendState;
import com.jeongjungang.ui.recommend.RecommendViewModel;
import com.jeongjungang.util.ExternalApps;
import com.jeongjungang.util.ShareText;

import java.util.ArrayList;
import java.util.List;

/** 추천 코어의 순서와 이유를 그대로 보여 주는 후보 비교·상세 화면. */
public class RecommendationActivity extends AppCompatActivity {

    private ActivityRecommendationBinding binding;
    private RecommendViewModel model;
    private RecommendState current;
    private final List<Participant> participants = new ArrayList<>();
    private final List<String> originLabels = new ArrayList<>();
    private Criterion criterion;
    private String selectedStation;
    private boolean detail;
    private int listScroll;
    private int detailScroll;

    /** 화면 간에는 표시 이름과 좌표만 전달하고 결과는 ViewModel에서 계산한다. */
    public static Intent createIntent(Context context, List<GeoPlace> origins, boolean fair) {
        ArrayList<Bundle> values = new ArrayList<>();
        for (GeoPlace origin : origins) {
            Bundle value = new Bundle();
            value.putString("label", origin.name);
            value.putDouble("lat", origin.location.lat);
            value.putDouble("lng", origin.location.lng);
            values.add(value);
        }

        return new Intent(context, RecommendationActivity.class)
                .putParcelableArrayListExtra("origins", values)
                .putExtra("fair", fair);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityRecommendationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        readInput(savedInstanceState);
        configureInsets();
        model = new ViewModelProvider(this).get(RecommendViewModel.class);
        binding.backButton.setOnClickListener(view -> goBack());
        binding.criterionButton.setOnClickListener(view -> chooseCriterion());
        binding.primaryButton.setOnClickListener(view -> primaryAction());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goBack();
            }
        });

        model.getState().observe(this, this::render);
        RecommendState previous = model.getState().getValue();
        if (previous == null || previous.status == RecommendState.Status.IDLE) {
            model.recommend(participants, criterion);
        }
    }

    /** 프로세스 재생성 시에도 출발지와 저장한 선택을 복원한다. */
    private void readInput(Bundle saved) {
        ArrayList<Bundle> values = getIntent().getParcelableArrayListExtra("origins");
        if (values != null) {
            for (int i = 0; i < values.size(); i++) {
                Bundle value = values.get(i);
                String name = value.getString("name",
                        i == 0 ? "나" : getString(R.string.origin_friend, i));
                participants.add(new Participant(name,
                        new LatLng(value.getDouble("lat"), value.getDouble("lng"))));
                originLabels.add(value.getString("label", "출발지"));
            }
        }

        boolean fair = saved == null ? getIntent().getBooleanExtra("fair", false)
                : saved.getBoolean("fair");
        criterion = fair ? Criterion.MAX_TIME : Criterion.TOTAL_TIME;
        if (saved != null) {
            selectedStation = saved.getString("selected");
            detail = saved.getBoolean("detail");
            listScroll = saved.getInt("listScroll");
            detailScroll = saved.getInt("detailScroll");
        }
    }

    /** 화면 높이에 맞춰 지도 공간을 줄여 가로 화면에서도 목록을 남긴다. */
    private void configureInsets() {
        WindowCompat.getInsetsController(getWindow(), binding.getRoot())
                .setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), binding.getRoot())
                .setAppearanceLightNavigationBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });

        binding.content.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int height = Math.min(dp(300), Math.max(dp(72), (b - t) * 38 / 100));
            if (binding.mapPlaceholder.getLayoutParams().height != height) {
                binding.mapPlaceholder.getLayoutParams().height = height;
                binding.mapPlaceholder.requestLayout();
            }
        });
    }

    /** 기준 변경은 기존 결과를 지우고 코어에서 다시 계산한다. */
    private void chooseCriterion() {
        String[] labels = {getString(R.string.recommend_total), getString(R.string.recommend_fair)};
        new MaterialAlertDialogBuilder(this).setTitle("추천 기준")
                .setSingleChoiceItems(labels, criterion == Criterion.TOTAL_TIME ? 0 : 1,
                        (dialog, which) -> {
                            dialog.dismiss();
                            Criterion next = which == 0 ? Criterion.TOTAL_TIME : Criterion.MAX_TIME;
                            if (next == criterion) return;
                            criterion = next;
                            selectedStation = null;
                            detail = false;
                            listScroll = 0;
                            model.recommend(participants, criterion);
                        }).setNegativeButton("닫기", null).show();
    }

    /** 로딩·빈 결과·오류에서는 이전 후보를 노출하지 않는다. */
    private void render(RecommendState state) {
        current = state;
        binding.resultList.removeAllViews();
        binding.criterionButton.setText("추천 기준 · " + getString(criterion == Criterion.TOTAL_TIME
                ? R.string.recommend_total : R.string.recommend_fair));
        boolean success = state.status == RecommendState.Status.SUCCESS;
        binding.mapPlaceholder.setVisibility(detail && success ? View.GONE : View.VISIBLE);
        binding.criterionButton.setVisibility(detail && success ? View.GONE : View.VISIBLE);
        binding.primaryButton.setEnabled(success);
        binding.primaryButton.setAlpha(success ? 1f : 0.45f);
        binding.primaryButton.setText(detail ? R.string.recommend_share : R.string.recommend_detail);

        if (!success) {
            String message = getString(R.string.recommend_loading);
            if (state.status == RecommendState.Status.EMPTY) message = getString(R.string.recommend_empty);
            if (state.status == RecommendState.Status.ERROR) message = state.errorMessage;
            binding.resultList.addView(text(message, 16, false));
            if (state.status == RecommendState.Status.ERROR || state.status == RecommendState.Status.EMPTY) {
                if (state.outOfArea.isEmpty()) {
                    addButton(binding.resultList, "다시 계산", () -> model.recommend(participants, criterion));
                }
                addButton(binding.resultList, "출발지 수정", this::finish);
            }
            return;
        }

        if (selected() == null) selectedStation = state.recommendations.get(0).station;
        if (detail) renderDetail(selected());
        else renderCandidates();
        int scroll = detail ? detailScroll : listScroll;
        binding.resultScroll.post(() -> binding.resultScroll.scrollTo(0, scroll));
    }

    /** 후보 순위는 추천 코어가 정한 순서이며, 선택 표시는 별도로 둔다. */
    private void renderCandidates() {
        for (int i = 0; i < current.recommendations.size(); i++) {
            Recommendation item = current.recommendations.get(i);
            boolean chosen = item.station.equals(selectedStation);
            LinearLayout card = column();
            card.setPadding(dp(14), dp(12), dp(16), dp(14));
            card.setBackgroundResource(chosen ? R.drawable.start_action_primary : R.drawable.start_action_secondary);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(12);
            card.setLayoutParams(params);
            card.addView(text((i + 1) + "  " + ShareText.stationLabel(item), 19, true));
            if (chosen) card.addView(text("선택됨", 12, true));
            card.addView(text(item.reason, 14, true));
            card.addView(text(criterion == Criterion.TOTAL_TIME
                    ? "이동시간 합계 " + Math.round(item.totalMinutes) + "분"
                    : "최대 이동시간 " + Math.round(item.maxMinutes) + "분", 12, false));
            addTrips(card, item, false);
            card.setSelected(chosen);
            card.setFocusable(true);
            card.setClickable(true);
            card.setContentDescription((i + 1) + "위, " + ShareText.stationLabel(item)
                    + (chosen ? ", 선택됨. " : ". ") + item.reason + ". " + tripSummary(item));
            card.setOnClickListener(view -> {
                listScroll = binding.resultScroll.getScrollY();
                selectedStation = item.station;
                render(current);
            });
            binding.resultList.addView(card);
        }
    }

    /** 구간별 경로는 만들지 않고 코어가 제공하는 총시간·환승만 표시한다. */
    private void renderDetail(Recommendation item) {
        binding.resultList.addView(text(ShareText.stationLabel(item), 26, true));
        LinearLayout summary = column();
        summary.setPadding(dp(16), dp(16), dp(16), dp(16));
        summary.setBackgroundResource(R.drawable.start_action_primary);
        summary.addView(text(item.reason, 16, true));
        summary.addView(text("이동시간 합계 " + Math.round(item.totalMinutes) + "분"
                + "  ·  최대 " + Math.round(item.maxMinutes) + "분", 16, true));
        binding.resultList.addView(summary);
        binding.resultList.addView(text("각자 얼마나 걸릴까?", 18, true));
        addTrips(binding.resultList, item, true);
        binding.resultList.addView(text("출발지에서 역까지의 접근시간이 포함된 근사값이에요. 실제 경로는 지도 앱에서 확인해 주세요.", 13, false));
        addButton(binding.resultList, "지도 앱에서 길찾기", this::chooseParticipant);
    }

    /** 후보 카드는 3명씩 비교하고, 상세·큰 글꼴에서는 세로로 나열한다. */
    private void addTrips(LinearLayout target, Recommendation item, boolean withOrigin) {
        boolean stacked = withOrigin || getResources().getConfiguration().fontScale > 1.2f;
        LinearLayout row = null;
        for (int i = 0; i < item.trips.size(); i++) {
            PersonTrip trip = item.trips.get(i);
            if (stacked) {
                target.addView(text(ShareText.tripLabel(trip), 14, false));
                if (withOrigin) target.addView(text("출발 · " + originLabels.get(i), 13, false));
                continue;
            }

            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                target.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout cell = column();
            cell.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            cell.addView(text(trip.name, 12, false));
            cell.addView(text(Math.round(trip.minutes) + "분", 20, true));
            cell.addView(text(trip.transfers == 0 ? "환승 없음" : "환승 " + trip.transfers + "회", 12, false));
            row.addView(cell);
        }
    }

    private String tripSummary(Recommendation item) {
        StringBuilder result = new StringBuilder();
        for (PersonTrip trip : item.trips) result.append(ShareText.tripLabel(trip)).append(". ");
        return result.toString();
    }

    /** 한 후보 상세에서는 Android의 실제 공유창을 연다. */
    private void primaryAction() {
        Recommendation item = selected();
        if (item == null) return;
        if (detail) {
            ExternalApps.share(this, ShareText.forOne(item));
        } else {
            listScroll = binding.resultScroll.getScrollY();
            detailScroll = 0;
            detail = true;
            render(current);
        }
    }

    /** 길찾기는 본인으로 고정하지 않고 출발 참가자를 먼저 선택한다. */
    private void chooseParticipant() {
        String[] choices = new String[participants.size()];
        for (int i = 0; i < choices.length; i++) {
            choices[i] = participants.get(i).name + " · " + originLabels.get(i);
        }
        new MaterialAlertDialogBuilder(this).setTitle("누구의 출발지에서 볼까요?")
                .setItems(choices, (dialog, which) -> chooseMap(which))
                .setNegativeButton("닫기", null).show();
    }

    /** 기존 지도 연결 유틸리티를 사용하며 네이버 앱 부재 시 대안을 제공한다. */
    private void chooseMap(int person) {
        Recommendation item = selected();
        if (item == null) return;
        LatLng destination = current.stationCoordinates.get(item.station);
        if (destination == null) {
            Snackbar.make(binding.getRoot(), "역 좌표를 찾지 못했어요.", Snackbar.LENGTH_LONG).show();
            return;
        }
        Participant from = participants.get(person);
        Runnable kakao = () -> ExternalApps.openKakaoRoute(this, originLabels.get(person),
                from.location, item.station + "역", destination);
        new MaterialAlertDialogBuilder(this).setTitle(item.station + "역까지 길찾기")
                .setItems(new String[]{"카카오맵", "네이버지도"}, (dialog, which) -> {
                    if (which == 0) {
                        kakao.run();
                    } else if (!ExternalApps.openNaverRoute(this, originLabels.get(person),
                            from.location, item.station + "역", destination)) {
                        new MaterialAlertDialogBuilder(this).setMessage("네이버지도를 열 수 없어요.")
                                .setPositiveButton("카카오맵으로 보기", (d, w) -> kakao.run())
                                .setNegativeButton("닫기", null).show();
                    }
                }).setNegativeButton("닫기", null).show();
    }

    private Recommendation selected() {
        if (current == null) return null;
        for (Recommendation item : current.recommendations) {
            if (item.station.equals(selectedStation)) return item;
        }
        return null;
    }

    private void goBack() {
        if (detail) {
            detail = false;
            render(current);
        } else finish();
    }

    /** 선택과 목록 위치는 회전·프로세스 재생성 후에도 유지한다. */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("fair", criterion == Criterion.MAX_TIME);
        state.putString("selected", selectedStation);
        state.putBoolean("detail", detail);
        state.putInt("listScroll", detail ? listScroll : binding.resultScroll.getScrollY());
        state.putInt("detailScroll", detail ? binding.resultScroll.getScrollY() : 0);
    }

    // 반복 UI의 기본 간격과 색상을 통일한다.
    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(ContextCompat.getColor(this, R.color.start_ink));
        view.setPadding(0, dp(5), 0, dp(5));
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private void addButton(LinearLayout target, String label, Runnable action) {
        AppCompatButton button = new AppCompatButton(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12);
        button.setLayoutParams(params);
        button.setMinHeight(dp(48));
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setText(label);
        button.setTextColor(ContextCompat.getColor(this, R.color.start_ink));
        button.setAllCaps(false);
        button.setBackgroundResource(R.drawable.start_action_secondary);
        button.setSupportBackgroundTintList(null);
        button.setOnClickListener(view -> action.run());
        target.addView(button);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
