package com.jeongjungang.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.jeongjungang.R;
import com.jeongjungang.data.remote.meeting.MeetingModels.InvitePreview;
import com.jeongjungang.data.remote.meeting.MeetingModels.Origin;
import com.jeongjungang.data.remote.meeting.MeetingModels.Status;
import com.jeongjungang.data.remote.meeting.MeetingRules;
import com.jeongjungang.databinding.ActivityJoinMeetingBinding;
import com.jeongjungang.domain.model.LatLng;
import com.jeongjungang.ui.meeting.ActionResult;
import com.jeongjungang.ui.meeting.MeetingViewModel;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 초대 확인 → 내 정보 입력 → 서버 참여 성공 후 대기방으로 이동한다. */
public class JoinMeetingActivity extends AppCompatActivity {

    private ActivityJoinMeetingBinding binding;
    private MeetingViewModel model;
    private Origin origin;
    private String verifiedCode;
    private boolean showingPreview;
    private boolean joinable;
    private boolean busy;

    private final ActivityResultLauncher<Intent> originPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() != RESULT_OK || data == null) return;
                origin = new Origin(data.getStringExtra("label"), new LatLng(
                        data.getDoubleExtra("lat", 0), data.getDoubleExtra("lng", 0)));
                showOrigin();
            });

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        EdgeToEdge.enable(this);
        binding = ActivityJoinMeetingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        model = new ViewModelProvider(this).get(MeetingViewModel.class);
        configureInsets();
        restore(saved);

        binding.backButton.setOnClickListener(v -> goBack());
        binding.submitButton.setOnClickListener(v -> { if (showingPreview) join(); else preview(); });
        binding.retryButton.setOnClickListener(v -> preview());
        binding.originButton.setOnClickListener(v -> chooseOrigin());
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateControls(); }
            @Override public void afterTextChanged(Editable s) { }
        };
        binding.codeInput.addTextChangedListener(watcher);
        binding.nickname.addTextChangedListener(watcher);
        binding.codeInput.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_GO) return false;
            preview();
            return true;
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { goBack(); }
        });

        // 회전 시 진행 중인 요청은 기존 ViewModel이 마저 처리한다.
        boolean requestPending = model.getAction().getValue() != null;
        model.getAction().observe(this, this::onAction);
        if (!requestPending && showingPreview && !isFinishing()) preview();
        if (!model.isAvailable()) binding.status.setText(R.string.join_unavailable);
        showOrigin();
        updateControls();
    }

    /** 키보드와 시스템 바를 피하고 긴 내용은 버튼 위에서 스크롤한다. */
    private void configureInsets() {
        WindowCompat.getInsetsController(getWindow(), binding.getRoot()).setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), binding.getRoot()).setAppearanceLightNavigationBars(true);
        ViewCompat.setAccessibilityHeading(binding.heading, true);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
    }

    /** 링크 정규화는 명세와 같은 기존 MeetingRules를 사용한다. */
    private void preview() {
        if (busy) return;
        if (!model.isAvailable()) { binding.status.setText(R.string.join_unavailable); return; }
        String code = MeetingRules.parseInviteCode(binding.codeInput.getText().toString());
        if (code == null) {
            binding.codeInput.setError(getString(R.string.join_invalid_code));
            binding.codeInput.requestFocus();
            return;
        }
        verifiedCode = code;
        joinable = false;
        binding.codeInput.setError(null);
        hideKeyboard();
        model.preview(code);
    }

    /** 확인했던 코드로만 참여하며, 출발지는 선택하지 않으면 null이다. */
    private void join() {
        if (busy || !joinable || verifiedCode == null) return;
        String name = binding.nickname.getText().toString().trim();
        String error = MeetingRules.checkNickname(name);
        if (error != null) {
            binding.nickname.setError(error);
            binding.nickname.requestFocus();
            return;
        }
        binding.nickname.setError(null);
        hideKeyboard();
        model.join(verifiedCode, name, origin);
    }

    /** 실패한 참여는 자동 재전송하지 않고 입력을 보존해 사용자에게 알린다. */
    private void onAction(ActionResult action) {
        if (action == null || action.kind != ActionResult.Kind.PREVIEW && action.kind != ActionResult.Kind.JOIN) return;
        busy = action.status == ActionResult.Status.RUNNING;
        binding.retryButton.setVisibility(View.GONE);
        if (busy) {
            binding.status.setText(action.kind == ActionResult.Kind.JOIN ? "약속에 참여하고 있어요…" : "초대를 확인하고 있어요…");
        } else if (action.status == ActionResult.Status.FAILED) {
            joinable = false;
            binding.status.setText(action.message);
            binding.retryButton.setVisibility(showingPreview ? View.VISIBLE : View.GONE);
            model.consumeAction();
        } else if (action.kind == ActionResult.Kind.PREVIEW) {
            showPreview(action.preview);
            model.consumeAction();
        } else {
            String id = action.membership.meetingId;
            model.consumeAction();
            startActivity(MeetingRoomActivity.createIntent(this, id, verifiedCode, null));
            finish();
        }
        updateControls();
    }

    /** 공개 미리보기의 필드만 표시한다. 타인의 출발지나 목적을 추측하지 않는다. */
    private void showPreview(InvitePreview preview) {
        showingPreview = true;
        binding.hostName.setText(preview.hostNickname + "의 초대");
        binding.meetingTitle.setText(preview.title == null || preview.title.trim().isEmpty() ? "우리 약속" : preview.title);
        String date = preview.meetAtMillis == null ? "날짜와 시간은 아직 정하지 않았어."
                : DateTimeFormatter.ofPattern("M월 d일 a h:mm", Locale.KOREAN)
                        .format(Instant.ofEpochMilli(preview.meetAtMillis).atZone(ZoneId.of("Asia/Seoul")));
        binding.meetingInfo.setText(date + "\n지금 " + preview.participantCount + "명이 참여하고 있어");
        joinable = preview.status == Status.OPEN && preview.participantCount < MeetingRules.MAX_PARTICIPANTS;
        binding.status.setText(preview.status != Status.OPEN ? "지금은 참여할 수 없는 약속이에요."
                : !joinable ? "참여 인원이 가득 찼어요." : "");
        if (!joinable) binding.retryButton.setVisibility(View.VISIBLE);
        binding.scroll.scrollTo(0, 0);
    }

    private void chooseOrigin() {
        if (busy) return;
        new MaterialAlertDialogBuilder(this).setTitle("내 출발지")
                .setItems(new String[]{"출발지 검색", "나중에 입력할게"}, (dialog, which) -> {
                    if (which == 0) originPicker.launch(new Intent(this, OriginActivity.class)
                            .putExtra(OriginActivity.PICK_ORIGIN, true));
                    else { origin = null; showOrigin(); }
                }).setNegativeButton("닫기", null).show();
    }

    private void showOrigin() {
        binding.originButton.setText(origin == null ? getString(R.string.meeting_origin_later) : origin.label);
    }

    private void updateControls() {
        binding.codePanel.setVisibility(showingPreview ? View.GONE : View.VISIBLE);
        binding.previewPanel.setVisibility(showingPreview ? View.VISIBLE : View.GONE);
        binding.footerHint.setVisibility(showingPreview ? View.GONE : View.VISIBLE);
        binding.codeInput.setEnabled(!busy);
        binding.nickname.setEnabled(!busy);
        binding.originButton.setEnabled(!busy && joinable);
        binding.retryButton.setEnabled(!busy);
        binding.submitButton.setText(busy ? "처리 중…" : getString(showingPreview ? R.string.join_submit : R.string.join_check));
        boolean enabled = !busy && (showingPreview ? joinable && !binding.nickname.getText().toString().trim().isEmpty()
                : !binding.codeInput.getText().toString().trim().isEmpty());
        binding.submitButton.setEnabled(enabled);
        binding.submitButton.setAlpha(enabled ? 1f : 0.45f);
    }

    private void goBack() {
        if (busy) { binding.status.setText("요청이 끝날 때까지 잠시 기다려 주세요."); return; }
        if (!showingPreview) { finish(); return; }
        showingPreview = false;
        joinable = false;
        binding.status.setText("");
        binding.retryButton.setVisibility(View.GONE);
        updateControls();
    }

    private void hideKeyboard() {
        binding.codeInput.clearFocus();
        binding.nickname.clearFocus();
        WindowCompat.getInsetsController(getWindow(), binding.getRoot()).hide(WindowInsetsCompat.Type.ime());
    }

    /** 회전·검색 왕복 뒤에도 입력을 유지하고, 복원된 미리보기는 서버에서 재확인한다. */
    private void restore(Bundle state) {
        if (state == null) return;
        binding.codeInput.setText(state.getString("input", ""));
        binding.nickname.setText(state.getString("name", ""));
        verifiedCode = state.getString("verifiedCode");
        showingPreview = state.getBoolean("showingPreview");
        binding.hostName.setText(state.getString("host", ""));
        binding.meetingTitle.setText(state.getString("title", ""));
        binding.meetingInfo.setText(state.getString("info", ""));
        if (state.containsKey("origin")) origin = new Origin(state.getString("origin"),
                new LatLng(state.getDouble("lat"), state.getDouble("lng")));
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("input", binding.codeInput.getText().toString());
        state.putString("name", binding.nickname.getText().toString());
        state.putString("verifiedCode", verifiedCode);
        state.putBoolean("showingPreview", showingPreview);
        state.putString("host", binding.hostName.getText().toString());
        state.putString("title", binding.meetingTitle.getText().toString());
        state.putString("info", binding.meetingInfo.getText().toString());
        if (origin != null) {
            state.putString("origin", origin.label);
            state.putDouble("lat", origin.location.lat);
            state.putDouble("lng", origin.location.lng);
        }
    }
}
