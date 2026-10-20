package com.jeongjungang.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.jeongjungang.R;
import com.jeongjungang.data.remote.meeting.MeetingModels.*;
import com.jeongjungang.data.remote.meeting.MeetingRules;
import com.jeongjungang.databinding.ActivityRecommendationBinding;
import com.jeongjungang.ui.map.MapPanel;
import com.jeongjungang.domain.model.LatLng;
import com.jeongjungang.domain.recommend.PersonTrip;
import com.jeongjungang.domain.recommend.Recommendation;
import com.jeongjungang.ui.meeting.ActionResult;
import com.jeongjungang.ui.meeting.MeetingState;
import com.jeongjungang.ui.meeting.MeetingViewModel;
import com.jeongjungang.util.ExternalApps;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 추천 후보를 실제 약속으로 확정하고, 확정 후에는 서버의 장소·시간을 표시한다. */
public class ConfirmMeetingActivity extends AppCompatActivity {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private ActivityRecommendationBinding binding;
    private MeetingViewModel model;
    private MeetingState state;
    private String meetingId;
    private Place candidate;
    private Long meetAt;
    private boolean dateEdited;
    private boolean busy;
    private boolean awaitingConfirmation;
    private String actionMessage = "";
    private MapPanel mapPanel;

    /** 코어에서 계산한 후보의 실제 좌표와 비교값을 넘긴다. 예시 값은 넣지 않는다. */
    public static Intent createIntent(Context context, String id, long version, Recommendation item, LatLng location) {
        ArrayList<Bundle> trips = new ArrayList<>();
        for (PersonTrip trip : item.trips) {
            Bundle data = new Bundle();
            data.putString("name", trip.name);
            data.putDouble("minutes", trip.minutes);
            data.putInt("transfers", trip.transfers);
            trips.add(data);
        }
        return new Intent(context, ConfirmMeetingActivity.class).putExtra("meetingId", id)
                .putExtra("meetingVersion", version).putExtra("placeName", item.station + "역")
                .putExtra("lat", location.lat).putExtra("lng", location.lng)
                .putIntegerArrayListExtra("lines", new ArrayList<>(item.lines))
                .putExtra("reason", item.reason).putParcelableArrayListExtra("trips", trips);
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        EdgeToEdge.enable(this);
        binding = ActivityRecommendationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        model = new ViewModelProvider(this).get(MeetingViewModel.class);
        meetingId = getIntent().getStringExtra("meetingId");
        if (meetingId == null) { finish(); return; }
        String name = getIntent().getStringExtra("placeName");
        ArrayList<Integer> lines = getIntent().getIntegerArrayListExtra("lines");
        if (name != null && lines != null) candidate = new Place(name,
                new LatLng(getIntent().getDoubleExtra("lat", 0), getIntent().getDoubleExtra("lng", 0)), lines);
        if (saved != null) {
            dateEdited = saved.getBoolean("dateEdited");
            awaitingConfirmation = saved.getBoolean("awaitingConfirmation");
            if (saved.containsKey("meetAt")) meetAt = saved.getLong("meetAt");
        }

        binding.criterionButton.setVisibility(View.GONE);
        mapPanel = MapPanel.attach(this, binding.mapArea, binding.mapPlaceholder);
        binding.backButton.setOnClickListener(v -> goBack());
        binding.primaryButton.setOnClickListener(v -> primaryAction());
        WindowCompat.getInsetsController(getWindow(), binding.getRoot()).setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), binding.getRoot()).setAppearanceLightNavigationBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        binding.content.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int height = Math.min(dp(278), Math.max(dp(72), (b - t) * 35 / 100));
            if (binding.mapArea.getLayoutParams().height != height) {
                binding.mapArea.getLayoutParams().height = height;
                binding.mapArea.requestLayout();
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { goBack(); }
        });
        model.getState().observe(this, value -> {
            state = value;
            if (isConfirmed()) awaitingConfirmation = false;
            render();
        });
        model.getAction().observe(this, this::onAction);
        model.open(meetingId);
    }

    @Override protected void onStart() {
        super.onStart();
        if (meetingId != null) model.startPolling();
    }

    @Override protected void onStop() {
        model.stopPolling();
        super.onStop();
    }

    private boolean ready() { return state != null && state.snapshot != null && state.message == null; }

    private boolean isConfirmed() {
        return state != null && state.snapshot != null && state.snapshot.meeting.status == Status.CONFIRMED
                && state.snapshot.meeting.place != null && state.snapshot.meeting.meetAtMillis != null;
    }

    private boolean candidateCurrent() {
        return state != null && state.snapshot != null && candidate != null
                && state.snapshot.version == getIntent().getLongExtra("meetingVersion", -1);
    }

    private boolean canConfirm() {
        return ready() && !busy && !awaitingConfirmation && candidateCurrent() && state.snapshot.amHost()
                && state.snapshot.meeting.status == Status.OPEN && meetAt != null;
    }

    /** 확정(또는 선택한) 장소와 출발지를 입력한 참가자들을 지도에 찍는다. */
    private void showOnMap(Snapshot snap, Place place) {
        List<MapPanel.Pin> pins = new ArrayList<>();
        for (Member member : snap.members) {
            if (member.origin != null) {
                pins.add(new MapPanel.Pin(member.nickname, member.origin.location, MapPanel.Kind.ORIGIN));
            }
        }
        pins.add(new MapPanel.Pin(place.name, place.location, MapPanel.Kind.SELECTED));
        mapPanel.show(pins);
    }

    /** 수정 성공만으로 확정 화면을 꾸미지 않고, 다시 조회한 서버 상태를 기준으로 한다. */
    private void render() {
        int scroll = binding.resultScroll.getScrollY();
        binding.resultList.removeAllViews();
        binding.primaryButton.setEnabled(false);
        binding.primaryButton.setAlpha(0.45f);
        binding.footerNotice.setText("");
        binding.mapPlaceholder.setText(R.string.recommend_map_pending);
        binding.primaryButton.setText("이 장소로 확정하기");
        if (state == null || state.snapshot == null) {
            addText(state != null && state.message != null ? state.message : "약속을 불러오고 있어요…", 15, false);
            if (state != null && state.status == MeetingState.Status.ERROR) addButton("다시 불러오기", model::refresh);
            return;
        }

        Snapshot snap = state.snapshot;
        boolean confirmed = isConfirmed();
        Place place = confirmed ? snap.meeting.place : candidate;
        if (confirmed || !dateEdited) meetAt = snap.meeting.meetAtMillis;
        if (place == null) {
            addText("약속이 변경됐어. 대기방에서 만날 곳을 다시 찾아줘.", 15, false);
            addButton("대기방으로", this::openRoom);
            return;
        }
        binding.mapPlaceholder.setText((confirmed ? "약속 확정\n" : "선택한 후보\n") + place.name
                + "\n\n" + getString(R.string.recommend_map_pending));
        showOnMap(snap, place);
        TextView title = addText(place.name, 26, true);
        title.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_point_pin, 0, 0, 0);
        title.setCompoundDrawablePadding(dp(8));
        if (confirmed) {
            TextView complete = addText("약속 확정", 12, true);
            complete.setTextColor(getColor(R.color.start_point));
            complete.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_point_check, 0, 0, 0);
            complete.setCompoundDrawablePadding(dp(4));
        }
        ViewCompat.setAccessibilityHeading(title, true);
        StringBuilder lines = new StringBuilder();
        for (Integer line : place.lines) {
            if (lines.length() > 0) lines.append(" · ");
            lines.append(line).append("호선");
        }
        addText(lines.toString(), 13, false);
        addText(confirmed ? (snap.meeting.title == null ? "우리 약속" : snap.meeting.title)
                : getIntent().getStringExtra("reason"), 14, false);
        addText("약속 시간", 14, true);
        addText(formatTime(meetAt), 18, true);
        if (!confirmed && snap.amHost()) {
            AppCompatButton change = addButton(meetAt == null ? "날짜와 시간 선택" : "약속 시간 변경", this::pickDate);
            change.setEnabled(ready() && !busy && !awaitingConfirmation && candidateCurrent());
        }

        if (!confirmed && candidateCurrent()) {
            addText("각자 걸리는 시간", 16, true);
            ArrayList<Bundle> trips = getIntent().getParcelableArrayListExtra("trips");
            LinearLayout row = null;
            boolean largeFont = getResources().getConfiguration().fontScale > 1.2f;
            if (trips != null) for (int i = 0; i < trips.size(); i++) {
                Bundle trip = trips.get(i);
                if (largeFont || i % 3 == 0) {
                    row = new LinearLayout(this);
                    binding.resultList.addView(row, new LinearLayout.LayoutParams(-1, -2));
                }
                TextView cell = new TextView(this);
                int transfers = trip.getInt("transfers");
                cell.setText(trip.getString("name") + "\n" + Math.round(trip.getDouble("minutes")) + "분\n"
                        + (transfers == 0 ? "환승 없음" : "환승 " + transfers + "회"));
                cell.setTextSize(14);
                cell.setTextColor(getColor(R.color.start_ink));
                cell.setPadding(dp(4), dp(12), dp(4), dp(12));
                row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
            }
            addText("소요시간은 근사값이야. 실제 경로는 지도에서 확인해줘.", 12, false);
        }

        if (confirmed) {
            addButton(getString(R.string.nearby_entry), this::openNearbyPlaces).setEnabled(ready());
            addButton("출발 알람 설정", () -> startActivity(
                    com.jeongjungang.ui.alarm.DepartureAlarmActivity.intent(this, meetingId)));
            addButton("내 출발지에서 길찾기", this::chooseMap).setEnabled(ready());
            binding.primaryButton.setText("약속 공유하기");
        } else if (!snap.amHost()) binding.primaryButton.setText("방장의 확정을 기다리는 중");
        else if (busy) binding.primaryButton.setText("확정 중…");
        boolean enabled = confirmed ? ready() && !busy : canConfirm();
        binding.primaryButton.setEnabled(enabled);
        binding.primaryButton.setAlpha(enabled ? 1f : 0.45f);
        binding.footerNotice.setText(confirmed ? "장소와 시간이 정해졌어. 여기서 만나자."
                : !candidateCurrent() ? "약속 정보가 바뀌었어. 대기방에서 다시 찾아줘."
                : !snap.amHost() ? "장소와 시간은 방장만 확정할 수 있어."
                : meetAt == null ? "확정하려면 날짜와 시간이 필요해."
                : "확정하면 모두의 약속에 장소와 시간이 반영돼.");
        if (!confirmed && !candidateCurrent()) addButton("대기방으로", this::openRoom);
        addText(state.message != null ? state.message : awaitingConfirmation
                ? "서버에 저장했어. 확정 결과를 확인하고 있어요…" : actionMessage, 13, false);
        if (state.message != null || awaitingConfirmation) addButton("다시 불러오기", model::refresh);
        binding.resultScroll.post(() -> binding.resultScroll.scrollTo(0, scroll));
    }

    /** 저장 직전에도 방장·약속 버전·시간을 확인하고 중복 제출을 막는다. */
    private void primaryAction() {
        if (isConfirmed() && ready()) {
            ExternalApps.share(this, "[정중앙] " + (state.snapshot.meeting.title == null ? "우리 약속" : state.snapshot.meeting.title)
                    + "\n" + state.snapshot.meeting.place.name + "\n약속 시간: "
                    + formatTime(state.snapshot.meeting.meetAtMillis));
            return;
        }
        if (!canConfirm()) return;
        String error = MeetingRules.checkMeetAt(meetAt, System.currentTimeMillis());
        if (error != null) { actionMessage = error; render(); return; }
        final long selectedTime = meetAt;
        new MaterialAlertDialogBuilder(this).setTitle("이 약속으로 확정할까?")
                .setMessage(candidate.name + "\n" + formatTime(selectedTime))
                .setNegativeButton("다시 확인할게", null)
                .setPositiveButton("확정하기", (dialog, which) -> {
                    if (!canConfirm()) { actionMessage = "약속 상태가 바뀌었어. 다시 확인해줘."; render(); return; }
                    actionMessage = "";
                    model.confirm(candidate, selectedTime);
                }).show();
    }

    private void pickDate() {
        if (!ready() || !state.snapshot.amHost() || busy || awaitingConfirmation) return;
        ZonedDateTime initial = meetAt == null ? ZonedDateTime.now(SEOUL) : Instant.ofEpochMilli(meetAt).atZone(SEOUL);
        new DatePickerDialog(this, (picker, year, month, day) -> new TimePickerDialog(this, (clock, hour, minute) -> {
            if (!ready() || !state.snapshot.amHost() || isConfirmed() || busy) return;
            meetAt = LocalDateTime.of(year, month + 1, day, hour, minute).atZone(SEOUL).toInstant().toEpochMilli();
            dateEdited = true;
            actionMessage = "";
            render();
        }, initial.getHour(), initial.getMinute(), true).show(), initial.getYear(), initial.getMonthValue() - 1,
                initial.getDayOfMonth()).show();
    }

    private void onAction(ActionResult action) {
        if (action == null || action.kind != ActionResult.Kind.UPDATE_MEETING) return;
        busy = action.status == ActionResult.Status.RUNNING;
        if (!busy) {
            awaitingConfirmation = action.status == ActionResult.Status.DONE;
            actionMessage = action.status == ActionResult.Status.FAILED ? action.message : "";
            model.consumeAction();
        }
        render();
    }

    /** 확정 장소와 본인의 실제 출발지로 기존 지도 연결 함수를 호출한다. */
    private void chooseMap() {
        if (!ready() || !isConfirmed()) return;
        Member me = state.snapshot.me();
        if (me == null || me.origin == null) {
            actionMessage = "대기방에서 내 출발지를 먼저 입력해줘.";
            render();
            return;
        }
        Place place = state.snapshot.meeting.place;
        Runnable kakao = () -> ExternalApps.openKakaoRoute(this, me.origin.label, me.origin.location, place.name, place.location);
        new MaterialAlertDialogBuilder(this).setTitle("길찾기")
                .setItems(new String[]{"카카오맵", "네이버지도"}, (dialog, which) -> {
                    if (which == 0) kakao.run();
                    else if (!ExternalApps.openNaverRoute(this, me.origin.label, me.origin.location, place.name, place.location))
                        new MaterialAlertDialogBuilder(this).setMessage("네이버지도를 열 수 없어요.")
                                .setPositiveButton("카카오맵으로 보기", (d, w) -> kakao.run()).setNegativeButton("닫기", null).show();
                }).setNegativeButton("닫기", null).show();
    }

    /** 확정된 장소 좌표와 약속 목적을 주변 장소 검색에 전달한다. */
    private void openNearbyPlaces() {
        if (!ready() || !isConfirmed()) return;
        startActivity(NearbyPlacesActivity.createIntent(this,
                state.snapshot.meeting.place, state.snapshot.meeting.purpose));
    }

    private void goBack() {
        if (busy) return;
        if (isConfirmed()) openRoom(); else finish();
    }

    private void openRoom() {
        startActivity(MeetingRoomActivity.createIntent(this, meetingId, null, null).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    private String formatTime(Long time) {
        return time == null ? "날짜와 시간을 선택해줘" : DateTimeFormatter.ofPattern("yyyy년 M월 d일 a h:mm", Locale.KOREAN)
                .format(Instant.ofEpochMilli(time).atZone(SEOUL));
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle saved) {
        super.onSaveInstanceState(saved);
        saved.putBoolean("dateEdited", dateEdited);
        saved.putBoolean("awaitingConfirmation", awaitingConfirmation);
        if (meetAt != null) saved.putLong("meetAt", meetAt);
    }

    private TextView addText(String value, int size, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(getColor(R.color.start_ink));
        text.setPadding(0, dp(6), 0, dp(6));
        if (bold) text.setTypeface(null, Typeface.BOLD);
        binding.resultList.addView(text);
        return text;
    }

    private AppCompatButton addButton(String label, Runnable action) {
        AppCompatButton button = new AppCompatButton(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(getColor(R.color.start_ink));
        button.setMinHeight(dp(48));
        button.setBackgroundResource(R.drawable.start_action_secondary);
        button.setSupportBackgroundTintList(null);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12);
        button.setLayoutParams(params);
        button.setOnClickListener(v -> action.run());
        binding.resultList.addView(button);
        return button;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
