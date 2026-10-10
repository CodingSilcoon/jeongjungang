package com.jeongjungang.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.jeongjungang.R;
import com.jeongjungang.data.remote.meeting.MeetingModels.Origin;
import com.jeongjungang.data.remote.meeting.MeetingModels.Purpose;
import com.jeongjungang.data.remote.meeting.MeetingRules;
import com.jeongjungang.databinding.ActivityCreateMeetingBinding;
import com.jeongjungang.domain.model.LatLng;
import com.jeongjungang.ui.meeting.ActionResult;
import com.jeongjungang.ui.meeting.MeetingViewModel;
import com.jeongjungang.util.ExternalApps;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 이름만 필수인 약속 생성 화면. 선택 항목은 입력하지 않으면 null로 전달한다. */
public class CreateMeetingActivity extends AppCompatActivity {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private ActivityCreateMeetingBinding binding;
    private MeetingViewModel model;
    private Origin origin;
    private Long meetAt;
    private boolean busy;
    private String inviteUrl;
    private String inviteCode;
    private String createdTitle;

    private final ActivityResultLauncher<Intent> originPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() != RESULT_OK || data == null) return;
                origin = new Origin(data.getStringExtra("label"), new LatLng(
                        data.getDoubleExtra("lat", 0), data.getDoubleExtra("lng", 0)));
                updateOptionalFields();
            });

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        binding = ActivityCreateMeetingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        model = new ViewModelProvider(this).get(MeetingViewModel.class);

        configureInsets();
        stylePurposes();
        restoreDraft(state);
        binding.backButton.setOnClickListener(view -> finish());
        binding.dateButton.setOnClickListener(view -> chooseDateAction());
        binding.originButton.setOnClickListener(view -> chooseOriginAction());
        binding.createButton.setOnClickListener(view -> submit());
        binding.nickname.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.nickname.setError(null);
                updateButton();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        updateOptionalFields();
        if (!model.isAvailable()) binding.status.setText(R.string.meeting_server_unavailable);
        if (inviteCode != null) showInvitation();
        model.getAction().observe(this, this::onAction);
        updateButton();
    }

    /** 키보드가 올라와도 하단 버튼과 입력칸에 접근할 수 있도록 여백을 적용한다. */
    private void configureInsets() {
        WindowCompat.getInsetsController(getWindow(), binding.getRoot()).setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), binding.getRoot()).setAppearanceLightNavigationBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
    }

    /** 선택 목적은 레몬색, 나머지는 흰색으로 표시한다. 작은 화면에서는 줄바꿈한다. */
    private void stylePurposes() {
        for (int i = 0; i < binding.purposeGroup.getChildCount(); i++) {
            Chip chip = (Chip) binding.purposeGroup.getChildAt(i);
            chip.setChipBackgroundColor(new ColorStateList(
                    new int[][]{{android.R.attr.state_checked}, {}},
                    new int[]{getColor(R.color.start_accent), getColor(R.color.start_surface)}));
            chip.setTextColor(getColor(R.color.start_ink));
            chip.setChipStrokeColor(ColorStateList.valueOf(getColor(R.color.start_ink)));
            chip.setChipStrokeWidth(getResources().getDisplayMetrics().density);
            chip.setCheckedIconVisible(false);
        }
    }

    /** 날짜 선택을 취소하면 기존 값을 유지한다. 선택 시각은 한국 시간으로 저장한다. */
    private void chooseDateAction() {
        new MaterialAlertDialogBuilder(this).setTitle("날짜와 시간")
                .setItems(new String[]{"날짜와 시간 선택", "나중에 정할게"}, (dialog, which) -> {
                    if (which == 1) {
                        meetAt = null;
                        updateOptionalFields();
                    } else pickDate();
                }).setNegativeButton("닫기", null).show();
    }

    private void pickDate() {
        ZonedDateTime initial = meetAt == null ? ZonedDateTime.now(SEOUL)
                : Instant.ofEpochMilli(meetAt).atZone(SEOUL);
        new DatePickerDialog(this, (picker, year, month, day) ->
                new TimePickerDialog(this, (clock, hour, minute) -> {
                    meetAt = LocalDateTime.of(year, month + 1, day, hour, minute)
                            .atZone(SEOUL).toInstant().toEpochMilli();
                    updateOptionalFields();
                }, initial.getHour(), initial.getMinute(), true).show(),
                initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
    }

    /** 출발지는 실제 검색으로 선택하며, 선택하지 않고 생성해도 된다. */
    private void chooseOriginAction() {
        new MaterialAlertDialogBuilder(this).setTitle("내 출발지")
                .setItems(new String[]{"출발지 검색", "나중에 입력할게"}, (dialog, which) -> {
                    if (which == 0) {
                        originPicker.launch(new Intent(this, OriginActivity.class)
                                .putExtra(OriginActivity.PICK_ORIGIN, true));
                    } else {
                        origin = null;
                        updateOptionalFields();
                    }
                }).setNegativeButton("닫기", null).show();
    }

    private void updateOptionalFields() {
        binding.dateButton.setText(meetAt == null ? getString(R.string.meeting_date_later)
                : DateTimeFormatter.ofPattern("yyyy.MM.dd (E) HH:mm", Locale.KOREAN)
                        .format(Instant.ofEpochMilli(meetAt).atZone(SEOUL)));
        binding.originButton.setText(origin == null ? getString(R.string.meeting_origin_later) : origin.label);
    }

    private Purpose purpose() {
        int id = binding.purposeGroup.getCheckedChipId();
        if (id == R.id.purposeMeal) return Purpose.MEAL;
        if (id == R.id.purposeCafe) return Purpose.CAFE;
        if (id == R.id.purposeDrink) return Purpose.DRINK;
        return Purpose.ETC;
    }

    /** 중복 요청을 막고 명세의 길이 검증을 통과한 값만 서버 계층에 보낸다. */
    private void submit() {
        if (inviteCode != null) {
            ExternalApps.share(this, "[정중앙] " + createdTitle + "\n초대 코드: " + inviteCode
                    + (inviteUrl == null ? "" : "\n" + inviteUrl));
            return;
        }
        if (busy) return;
        String name = binding.nickname.getText().toString().trim();
        String title = binding.titleInput.getText().toString().trim();
        String error = MeetingRules.checkNickname(name);
        if (error != null) {
            binding.nickname.setError(error);
            binding.nickname.requestFocus();
            return;
        }
        error = MeetingRules.checkTitle(title);
        if (error != null) {
            binding.titleInput.setError(error);
            return;
        }
        if (!model.isAvailable()) {
            binding.status.setText(R.string.meeting_server_unavailable);
            return;
        }
        WindowCompat.getInsetsController(getWindow(), binding.getRoot()).hide(WindowInsetsCompat.Type.ime());
        model.create(name, title.isEmpty() ? null : title, purpose(), meetAt, origin);
    }

    /** 서버 응답이 성공한 경우에만 초대 코드를 보여 준다. 실패해도 입력은 보존한다. */
    private void onAction(ActionResult action) {
        if (action == null || action.kind != ActionResult.Kind.CREATE) return;
        busy = action.status == ActionResult.Status.RUNNING;
        setFormEnabled(binding.form, !busy);
        if (busy) {
            binding.status.setText("약속을 만들고 있어요…");
        } else if (action.status == ActionResult.Status.FAILED) {
            binding.status.setText(action.message);
            model.consumeAction();
        } else {
            inviteCode = action.membership.meeting.inviteCode;
            inviteUrl = action.membership.meeting.inviteUrl;
            createdTitle = action.membership.meeting.title;
            if (createdTitle == null || createdTitle.trim().isEmpty()) createdTitle = "우리 약속";
            model.consumeAction();
            startActivity(MeetingRoomActivity.createIntent(this, action.membership.meetingId,
                    inviteCode, inviteUrl));
            finish();
        }
        updateButton();
    }

    /** 생성 완료 후에는 재생성 버튼 대신 기존 약속의 초대 공유를 제공한다. */
    private void showInvitation() {
        binding.heading.setText("약속을 만들었어!");
        binding.form.setVisibility(View.GONE);
        binding.invitation.setVisibility(View.VISIBLE);
        binding.invitation.setText(createdTitle + "\n\n초대 코드\n" + inviteCode
                + (inviteUrl == null ? "" : "\n\n" + inviteUrl));
        binding.status.setText("");
        binding.footerHint.setText("초대 링크나 코드를 친구에게 보내줘.");
        binding.scroll.scrollTo(0, 0);
    }

    private void updateButton() {
        binding.createButton.setText(inviteCode != null ? "초대 공유하기" : busy ? "만드는 중…" : "약속 만들기");
        boolean enabled = !busy && (inviteCode != null || !binding.nickname.getText().toString().trim().isEmpty());
        binding.createButton.setEnabled(enabled);
        binding.createButton.setAlpha(enabled ? 1f : 0.48f);
    }

    private void setFormEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) setFormEnabled(group.getChildAt(i), enabled);
        }
    }

    /** 화면 회전·검색 왕복·프로세스 재생성 시 초안과 생성 결과를 유지한다. */
    private void restoreDraft(Bundle state) {
        if (state == null) return;
        binding.nickname.setText(state.getString("nickname", ""));
        binding.titleInput.setText(state.getString("title", ""));
        binding.purposeGroup.check(state.getInt("purpose", R.id.purposeEtc));
        if (state.containsKey("meetAt")) meetAt = state.getLong("meetAt");
        if (state.containsKey("origin")) origin = new Origin(state.getString("origin"),
                new LatLng(state.getDouble("lat"), state.getDouble("lng")));
        inviteCode = state.getString("inviteCode");
        inviteUrl = state.getString("inviteUrl");
        createdTitle = state.getString("createdTitle");
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("nickname", binding.nickname.getText().toString());
        state.putString("title", binding.titleInput.getText().toString());
        state.putInt("purpose", binding.purposeGroup.getCheckedChipId());
        if (meetAt != null) state.putLong("meetAt", meetAt);
        if (origin != null) {
            state.putString("origin", origin.label);
            state.putDouble("lat", origin.location.lat);
            state.putDouble("lng", origin.location.lng);
        }
        state.putString("inviteCode", inviteCode);
        state.putString("inviteUrl", inviteUrl);
        state.putString("createdTitle", createdTitle);
    }
}
