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
import com.jeongjungang.databinding.ViewRecommendStateBinding;
import com.jeongjungang.domain.model.LatLng;
import com.jeongjungang.domain.recommend.Criterion;
import com.jeongjungang.domain.recommend.Participant;
import com.jeongjungang.domain.recommend.PersonTrip;
import com.jeongjungang.domain.recommend.Recommendation;
import com.jeongjungang.ui.map.MapPanel;
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
    private MapPanel mapPanel;

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
        mapPanel = MapPanel.attach(this, binding.mapArea, binding.mapPlaceholder);
        model = new ViewModelProvider(this).get(RecommendViewModel.class);
        binding.backButton.setOnClickListener(view -> goBack());
        binding.criterionButton.setOnClickListener(view -> chooseCriterion());
        binding.primaryButton.setOnClickListener(view -> primaryAction());
        binding.stateSecondary.setOnClickListener(view -> {
            if (current != null && current.status == RecommendState.Status.EMPTY) retryRecommendation();
            else returnToOrigins();
        });

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
            if (binding.mapArea.getLayoutParams().height != height) {
                binding.mapArea.getLayoutParams().height = height;
                binding.mapArea.requestLayout();
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
        binding.mapArea.setVisibility(success && !detail ? View.VISIBLE : View.GONE);
        binding.criterionButton.setVisibility(success && !detail ? View.VISIBLE : View.GONE);
        binding.footerNotice.setVisibility(success ? View.VISIBLE : View.GONE);
        binding.stateSecondary.setVisibility(View.GONE);
        binding.primaryButton.setEnabled(success);
        binding.primaryButton.setAlpha(success ? 1f : 0.45f);
        binding.primaryButton.setText(detail ? R.string.recommend_share : R.string.recommend_detail);

        if (!success) {
            renderRecommendationState(state);
            return;
        }

        if (selected() == null) selectedStation = state.recommendations.get(0).station;
        if (detail) renderDetail(selected());
        else renderCandidates();
        showOnMap(state);
        int scroll = detail ? detailScroll : listScroll;
        binding.resultScroll.post(() -> binding.resultScroll.scrollTo(0, scroll));
    }

    /** 출발지와 후보 3곳을 지도에 찍는다. 선택한 후보는 크게 표시한다. */
    private void showOnMap(RecommendState state) {
        List<MapPanel.Pin> pins = new ArrayList<>();
        for (Participant person : state.participants) {
            pins.add(new MapPanel.Pin(person.name, person.location, MapPanel.Kind.ORIGIN));
        }
        for (int i = 0; i < state.recommendations.size(); i++) {
            Recommendation item = state.recommendations.get(i);
            LatLng at = state.stationCoordinates.get(item.station);
            if (at == null) continue;
            boolean chosen = item.station.equals(selectedStation);
            // 후보끼리 가까워서 이름을 다 쓰면 겹친다. 고른 후보만 이름, 나머지는 순위만
            pins.add(new MapPanel.Pin(chosen ? (i + 1) + " " + item.station + "역" : String.valueOf(i + 1),
                    at, chosen ? MapPanel.Kind.SELECTED : MapPanel.Kind.CANDIDATE));
        }
        mapPanel.show(pins);
    }

    /** 기존 추천 상태를 그대로 표시하며 실패를 빈 결과나 통신 오류로 바꾸지 않는다. */
    private void renderRecommendationState(RecommendState state) {
        ViewRecommendStateBinding panel = ViewRecommendStateBinding.inflate(
                getLayoutInflater(), binding.resultList, false);
        binding.resultList.addView(panel.getRoot());
        boolean outside = !state.outOfArea.isEmpty();
        boolean empty = state.status == RecommendState.Status.EMPTY;
        boolean loading = state.status == RecommendState.Status.LOADING
                || state.status == RecommendState.Status.IDLE;
        boolean meeting = getIntent().hasExtra("meetingId");

        panel.stateTitle.setText(outside ? "이 출발지는\n아직 지원하지 않아요"
                : empty ? "함께 갈 수 있는 곳을\n찾지 못했어요"
                : loading ? "만나기 좋은 곳을\n찾고 있어요" : "잠깐, 계산을\n마치지 못했어요");
        panel.stateCopy.setText(outside ? "지금은 서울교통공사\n1~8호선 근처만 지원해요."
                : empty ? "입력한 출발지로는 모두가 갈 수 있는\n추천 후보를 찾지 못했어요."
                : loading ? "각자의 이동시간과 환승을 비교해\n함께 만날 후보를 찾고 있어요."
                : state.errorMessage == null ? "추천을 계산하는 중 문제가 생겼어요. 다시 계산해 주세요."
                : state.errorMessage);
        panel.stateBadge.setText(outside ? "!" : empty ? "?" : loading ? "…" : "!");
        panel.stateIcon.setImageResource(outside || empty ? R.drawable.ic_point_pin
                : loading ? R.drawable.ic_recommend_route : R.drawable.ic_recommend_alert);
        panel.stateHint.setText(outside || empty
                ? meeting ? "약속방에서 각자 본인의 출발지를 확인해 주세요."
                          : "출발지 입력 화면에서 위치를 확인해 주세요."
                : loading ? "예상 이동시간은 실제 교통 상황과 다를 수 있어요."
                : "입력한 출발지와 추천 기준으로 다시 계산해요.");
        ViewCompat.setAccessibilityHeading(panel.stateTitle, true);

        // 이름이 같은 참가자도 입력 순서와 객체를 기준으로 실제 출발지를 찾는다.
        for (Participant person : state.outOfArea) {
            int index = state.participants.indexOf(person);
            String label = index >= 0 && index < originLabels.size() ? originLabels.get(index) : "출발지";
            TextView entry = text(person.name + " · " + label + "\n지원 지역 밖", 14, true);
            entry.setPadding(dp(14), dp(14), dp(14), dp(14));
            entry.setBackgroundResource(R.drawable.meeting_role_badge);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(16);
            panel.outsideList.addView(entry, params);
        }
        if (loading) {
            panel.stateCriterion.setVisibility(View.VISIBLE);
            panel.stateCriterion.setText(binding.criterionButton.getText());
        }

        String originsAction = meeting ? "약속방으로 돌아가기" : outside ? "출발지 수정" : "출발지 확인";
        binding.primaryButton.setText(loading ? "계산 중" : outside || empty ? originsAction : "다시 계산");
        binding.primaryButton.setEnabled(!loading);
        binding.primaryButton.setAlpha(loading ? 0.45f : 1f);
        binding.stateSecondary.setVisibility(outside ? View.GONE : View.VISIBLE);
        binding.stateSecondary.setText(empty ? "다시 계산"
                : meeting ? "약속방으로 돌아가기" : loading ? "출발지로 돌아가기" : "출발지 확인");
        binding.resultScroll.post(() -> binding.resultScroll.scrollTo(0, 0));
    }

    /** 동일한 출발지·기준으로 다시 계산하고 이전 상세 선택은 비운다. */
    private void retryRecommendation() {
        if (current == null || current.status == RecommendState.Status.LOADING) return;
        selectedStation = null;
        detail = false;
        listScroll = 0;
        detailScroll = 0;
        model.recommend(participants, criterion);
    }

    /** 호출한 입력 화면 또는 약속방으로 돌아가 기존 수정 권한을 유지한다. */
    private void returnToOrigins() {
        model.reset();
        finish();
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
            if (chosen) {
                TextView selectedLabel = text("선택됨", 12, true);
                selectedLabel.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_point_check, 0, 0, 0);
                selectedLabel.setCompoundDrawablePadding(dp(4));
                card.addView(selectedLabel);
            }
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
        binding.resultList.addView(text("각자 걸리는 시간", 18, true));
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
        if (current == null) return;
        if (current.status != RecommendState.Status.SUCCESS) {
            if (!current.outOfArea.isEmpty() || current.status == RecommendState.Status.EMPTY) returnToOrigins();
            else if (current.status == RecommendState.Status.ERROR) retryRecommendation();
            return;
        }
        Recommendation item = selected();
        if (item == null) return;
        String meetingId = getIntent().getStringExtra("meetingId");
        if (!detail && meetingId != null) {
            LatLng location = current.stationCoordinates.get(item.station);
            if (location == null) return;
            startActivity(ConfirmMeetingActivity.createIntent(this, meetingId,
                    getIntent().getLongExtra("meetingVersion", -1), item, location));
            return;
        }
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
        if (current == null || current.status != RecommendState.Status.SUCCESS) {
            returnToOrigins();
            return;
        }
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
