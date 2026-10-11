package com.jeongjungang.ui.alarm;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.jeongjungang.R;
import com.jeongjungang.alarm.AlarmPermissions;
import com.jeongjungang.alarm.AlarmSpec;
import com.jeongjungang.data.remote.meeting.MeetingModels.*;
import com.jeongjungang.data.repository.AccountabilityRepository.SyncResult;
import com.jeongjungang.databinding.ActivityDepartureAlarmBinding;
import com.jeongjungang.domain.alarm.AlarmTimeCalculator;
import com.jeongjungang.ui.meeting.AccountabilityViewModel;
import com.jeongjungang.ui.meeting.MeetingState;
import com.jeongjungang.ui.meeting.MeetingViewModel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** 준비시간과 동의는 본인, 책임 알람 켜기·끄기는 방장만 변경한다. */
public class DepartureAlarmActivity extends AppCompatActivity {

    private ActivityDepartureAlarmBinding binding;
    private MeetingViewModel meetings;
    private AccountabilityViewModel alarms;
    private MeetingState meetingState;
    private AccountabilityViewModel.State alarmState;
    private String meetingId;
    private boolean initialized;
    private boolean editing;
    private boolean bindingInputs;
    private boolean previousBusy;
    private boolean optionsEdited;
    private int margin = 5;
    private int grace = 60;
    private String notice = "";

    public static Intent intent(Context context, String meetingId) {
        return new Intent(context, DepartureAlarmActivity.class).putExtra("meetingId", meetingId);
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        EdgeToEdge.enable(this);
        binding = ActivityDepartureAlarmBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsets();
        meetingId = getIntent().getStringExtra("meetingId");
        if (meetingId == null) { finish(); return; }
        meetings = new ViewModelProvider(this).get(MeetingViewModel.class);
        alarms = new ViewModelProvider(this).get(AccountabilityViewModel.class);

        if (saved != null) {
            initialized = saved.getBoolean("initialized");
            editing = saved.getBoolean("editing");
            binding.prepInput.setText(saved.getString("prep", ""));
            binding.consent.setChecked(saved.getBoolean("consent"));
            margin = saved.getInt("margin", 5);
            grace = saved.getInt("grace", 60);
            optionsEdited = saved.getBoolean("optionsEdited");
        }
        bindActions();
        meetings.getState().observe(this, value -> {
            meetingState = value;
            Snapshot snap = snapshot();
            if (snap != null) {
                Member me = snap.me();
                if (me != null && (!initialized || !editing)) {
                    bindingInputs = true;
                    binding.prepInput.setText(me.prepMinutes == null ? "30" : String.valueOf(me.prepMinutes));
                    binding.consent.setChecked(me.optedIn);
                    bindingInputs = false;
                    initialized = true;
                }
                if (me != null && prep() != null && me.prepMinutes != null
                        && prep().equals(me.prepMinutes) && binding.consent.isChecked() == me.optedIn) editing = false;
                if (snap.meeting.accountability != null && (!optionsEdited || enabled())) {
                    margin = snap.meeting.accountability.marginMinutes;
                    grace = snap.meeting.accountability.gracePeriodSec;
                    optionsEdited = false;
                }
                alarms.onSnapshot(snap);
            }
            render();
        });
        alarms.getState().observe(this, value -> {
            alarmState = value;
            if (value.message != null) notice = value.message;
            boolean completed = previousBusy && !value.busy;
            previousBusy = value.busy;
            if (completed) meetings.refresh();
            render();
        });
        meetings.open(meetingId);
    }

    /** 입력 중인 값은 폴링으로 덮어쓰지 않고 저장된 상태와 따로 유지한다. */
    private void bindActions() {
        binding.prepInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!bindingInputs) { editing = true; render(); }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        binding.consent.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingInputs) { editing = true; render(); }
        });
        binding.minusButton.setOnClickListener(v -> step(-5));
        binding.plusButton.setOnClickListener(v -> step(5));
        binding.saveButton.setOnClickListener(v -> {
            if (!ready() || busy() || prep() == null) return;
            notice = "";
            alarms.consumeMessage();
            alarms.savePreferences(meetingId, prep(), binding.consent.isChecked());
        });
        binding.enableButton.setOnClickListener(v -> changeEnabled());
        binding.optionsButton.setOnClickListener(v -> chooseOptions());
        binding.permissionsButton.setOnClickListener(v -> startActivity(new Intent(this, AlarmPermissionsActivity.class)));
        binding.retryButton.setOnClickListener(v -> {
            notice = "";
            meetings.refresh();
            alarms.resync(snapshot());
        });
        binding.backButton.setOnClickListener(v -> leave());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { leave(); }
        });
    }

    private Snapshot snapshot() { return meetingState == null ? null : meetingState.snapshot; }
    private boolean busy() { return alarmState != null && alarmState.busy; }
    private boolean ready() { return snapshot() != null && snapshot().me() != null
            && meetingState.message == null && alarms.isAvailable(); }
    private boolean enabled() { return snapshot() != null && snapshot().meeting.accountability != null
            && snapshot().meeting.accountability.enabled; }

    private Integer prep() {
        try {
            int value = Integer.parseInt(binding.prepInput.getText().toString());
            return value >= 0 && value <= 240 ? value : null;
        } catch (NumberFormatException ignored) { return null; }
    }

    private void step(int delta) {
        if (prep() != null) binding.prepInput.setText(String.valueOf(Math.max(0, Math.min(240, prep() + delta))));
    }

    /** 서버에 저장된 동의만 사용한다. 저장 전 체크로 활성화하지 않는다. */
    private boolean allConsented() {
        if (snapshot() == null || snapshot().members.isEmpty()) return false;
        for (Member member : snapshot().members) if (!member.optedIn) return false;
        return true;
    }

    private boolean dirty() {
        Member me = snapshot() == null ? null : snapshot().me();
        return me == null || prep() == null || !prep().equals(me.prepMinutes)
                || binding.consent.isChecked() != me.optedIn;
    }

    private boolean canEnable() {
        return ready() && !busy() && snapshot().amHost() && allConsented() && !dirty()
                && snapshot().meeting.status == Status.CONFIRMED && snapshot().meeting.place != null
                && snapshot().meeting.meetAtMillis != null;
    }

    private void changeEnabled() {
        if (!ready() || busy() || !snapshot().amHost()) return;
        if (enabled()) {
            new MaterialAlertDialogBuilder(this).setTitle("책임 알람을 끌까요?")
                    .setMessage("이 약속의 모든 참가자 알람이 취소됩니다.")
                    .setNegativeButton("유지하기", null)
                    .setPositiveButton("끄기", (dialog, which) -> {
                        if (ready() && !busy() && snapshot().amHost()) alarms.disable(meetingId);
                    }).show();
        } else if (canEnable()) {
            alarms.enable(meetingId, grace, margin);
        }
    }

    /** 공통 설정은 책임 알람이 꺼져 있을 때 명세 범위 안에서 선택한다. */
    private void chooseOptions() {
        if (!ready() || busy() || !snapshot().amHost() || enabled()) return;
        new MaterialAlertDialogBuilder(this).setTitle("공통 알람 설정")
                .setItems(new String[]{"여유시간 · " + margin + "분", "알람 끄기 유예시간 · " + grace + "초"},
                        (dialog, which) -> {
                            int[] values = which == 0 ? new int[]{0, 5, 10, 15} : new int[]{30, 60, 120, 300};
                            String[] labels = new String[values.length];
                            for (int i = 0; i < values.length; i++) labels[i] = values[i] + (which == 0 ? "분" : "초");
                            new MaterialAlertDialogBuilder(this).setTitle(which == 0 ? "여유시간" : "유예시간")
                                    .setItems(labels, (d, selected) -> {
                                        if (!ready() || busy() || !snapshot().amHost() || enabled()) return;
                                        if (which == 0) margin = values[selected]; else grace = values[selected];
                                        optionsEdited = true;
                                        render();
                                    }).setNegativeButton("취소", null).show();
                        }).setNegativeButton("닫기", null).show();
    }

    /** 서버 상태·입력 초안·기기 예약 결과를 구분해서 화면에 표시한다. */
    private void render() {
        if (binding == null || alarms == null) return;
        Snapshot snap = snapshot();
        boolean writable = ready() && !busy();
        binding.prepInput.setEnabled(writable);
        binding.consent.setEnabled(writable);
        binding.minusButton.setEnabled(writable && prep() != null && prep() > 0);
        binding.plusButton.setEnabled(writable && prep() != null && prep() < 240);
        binding.saveButton.setEnabled(writable && prep() != null && dirty());
        binding.saveButton.setAlpha(binding.saveButton.isEnabled() ? 1f : .45f);
        binding.saveButton.setText(busy() ? "처리 중…" : dirty() ? "내 설정 저장하기" : "내 설정 저장됨");
        binding.permissionsButton.setText(AlarmPermissions.isReady(this) ? "알람 권한 · 준비됨" : "알람 권한 · 확인 필요");
        binding.hostControls.setVisibility(snap != null && snap.amHost() ? View.VISIBLE : View.GONE);
        binding.people.removeAllViews();
        binding.feedback.setText(!alarms.isAvailable() ? "서버 연결 후 사용할 수 있어요."
                : meetingState != null && meetingState.message != null ? meetingState.message : notice);
        binding.retryButton.setEnabled(!busy() && alarms.isAvailable());
        if (snap == null) {
            binding.meetingSummary.setText(meetingState != null && meetingState.status == MeetingState.Status.GONE
                    ? "더 이상 참여할 수 없는 약속입니다." : "약속 정보를 불러오는 중입니다.");
            binding.alarmTime.setText("— —");
            binding.timeHint.setText("약속 정보를 확인하면 예상 시각을 계산합니다.");
            return;
        }

        binding.meetingSummary.setText((snap.meeting.place == null ? "장소 미정" : snap.meeting.place.name)
                + "\n약속 시간 · " + (snap.meeting.meetAtMillis == null ? "미정" : format(snap.meeting.meetAtMillis)));
        binding.consentExplanation.setText("누군가 알람을 " + grace + "초 안에 끄지 않으면 나머지 친구들에게도 알람이 울려요. 내가 끄지 않아도 마찬가지예요.");
        int count = 0;
        for (Member member : snap.members) {
            if (member.optedIn) count++;
            TextView row = new TextView(this);
            row.setText(member.nickname + (member.id.equals(snap.myParticipantId) ? " · 나" : "")
                    + (member.role == Role.HOST ? " · 방장" : "") + "\n"
                    + (member.optedIn ? "✓ 동의 완료" : "동의 대기"));
            row.setTextSize(13);
            row.setTextColor(getColor(member.optedIn ? R.color.start_point : R.color.start_muted));
            row.setPadding(0, dp(10), 0, dp(10));
            binding.people.addView(row);
        }
        binding.peopleTitle.setText("친구들 참여 상태 · " + count + " / " + snap.members.size() + " 동의");
        binding.enableButton.setText(enabled() ? "책임 알람 끄기" : "책임 알람 켜기");
        binding.enableButton.setEnabled(writable && snap.amHost() && (enabled() || canEnable()));
        binding.enableButton.setAlpha(binding.enableButton.isEnabled() ? 1f : .45f);
        binding.optionsButton.setEnabled(writable && !enabled());
        binding.optionsButton.setText("공통 설정 · 여유 " + margin + "분 / 유예 " + grace + "초");
        binding.hostHint.setText(enabled() ? "책임 알람이 켜져 있어요. 각자의 기기 예약 상태는 따로 확인해 주세요."
                : dirty() ? "내 설정을 먼저 저장해 주세요. 전원 동의 후 방장이 켤 수 있어요."
                : allConsented() ? "모두 동의했어요. 방장이 책임 알람을 켤 수 있어요."
                : "전원이 동의하면 방장이 책임 알람을 켤 수 있어요.");
        renderTime(snap);
    }

    private void renderTime(Snapshot snap) {
        Double travel = alarmState == null ? null : alarmState.travelMinutes;
        Member me = snap.me();
        boolean calculable = me != null && me.origin != null && travel != null && Double.isFinite(travel)
                && travel >= 0 && prep() != null && snap.meeting.meetAtMillis != null && snap.meeting.place != null;
        AlarmSpec local = alarms.localAlarm(meetingId);
        boolean scheduled = enabled() && me != null && me.optedIn && !dirty() && !optionsEdited
                && local != null && local.fireAtMillis > System.currentTimeMillis()
                && alarmState != null && alarmState.sync == SyncResult.SCHEDULED
                && AlarmPermissions.canScheduleExactAlarms(this);
        binding.timeLabel.setText(scheduled ? "내 알람 시각 · 기기 예약됨" : "내 예상 알람 시각");
        Long fireAt = null;
        if (scheduled) fireAt = local.fireAtMillis;
        else if (calculable) fireAt = AlarmTimeCalculator.fireAtMillis(
                snap.meeting.meetAtMillis, travel, prep(), margin);
        binding.alarmTime.setText(fireAt == null ? "— —" : format(fireAt));
        String hint = scheduled ? "서버 시각을 반영해 이 기기에 예약했어요."
                : !calculable ? "내 출발지와 준비시간을 확인해 주세요. 이동시간 계산 후 표시됩니다."
                : dirty() ? "저장 전 예상 시각이에요."
                : enabled() ? "서버·기기 예약 확인 중입니다. 아직 예약 완료가 아니에요." : "예상 시각이에요. 아직 예약 전입니다.";
        if (!AlarmPermissions.isReady(this)) hint += "\n알람 권한을 확인해 주세요.";
        if (fireAt != null && fireAt <= System.currentTimeMillis()) hint += "\n알람 예정 시각이 이미 지났어요.";
        if (fireAt != null && AlarmTimeCalculator.isLateNight(fireAt, TimeZone.getDefault())) hint += "\n심야 시간대에 알람이 울려요.";
        binding.timeHint.setText(hint);
        binding.breakdown.setText("이동 " + (calculable ? "약 " + (int)Math.ceil(travel) + "분" : "계산 전")
                + " · 준비 " + (prep() == null ? "0~240분 입력" : prep() + "분") + " · 여유 " + margin + "분");
    }

    private void leave() {
        if (editing && dirty()) {
            new MaterialAlertDialogBuilder(this).setMessage("저장하지 않은 변경을 나중에 다시 입력할까요?")
                    .setNegativeButton("계속 수정", null).setPositiveButton("나가기", (d, w) -> finish()).show();
        } else finish();
    }

    @Override protected void onStart() {
        super.onStart();
        if (meetings != null) { meetings.startPolling(); alarms.resync(snapshot()); }
    }

    @Override protected void onStop() {
        if (meetings != null) meetings.stopPolling();
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("initialized", initialized);
        out.putBoolean("editing", editing);
        out.putString("prep", binding.prepInput.getText().toString());
        out.putBoolean("consent", binding.consent.isChecked());
        out.putInt("margin", margin);
        out.putInt("grace", grace);
        out.putBoolean("optionsEdited", optionsEdited);
        super.onSaveInstanceState(out);
    }

    private String format(long millis) {
        return new SimpleDateFormat("M월 d일 a h:mm", Locale.KOREAN).format(new Date(millis));
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void applyInsets() {
        WindowCompat.getInsetsController(getWindow(), binding.getRoot()).setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), binding.getRoot()).setAppearanceLightNavigationBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
    }
}
