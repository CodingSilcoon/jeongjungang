package com.jeongjungang.ui;

import android.content.Context;
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

    private static final String EXTRA_INVITE = "invite";

    /** 초대 링크나 코드로 바로 연다. 코드 칸에 채우고 미리보기까지 자동으로 확인한다. */
    public static Intent createIntent(Context context, String inviteLinkOrCode) {
        return new Intent(context, JoinMeetingActivity.class).putExtra(EXTRA_INVITE, inviteLinkOrCode);
    }

    private ActivityJoinMeetingBinding binding;
    private MeetingViewModel model;
    private Origin origin;
    private String verifiedCode;
    private boolean showingPreview;
    private boolean joinable;
    private boolean busy;
    private String errorScreen = "";

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
        binding.submitButton.setOnClickListener(v -> {
            if (!errorScreen.isEmpty()) {
                if ("NETWORK".equals(errorScreen)) preview(); else goHome();
            } else if (showingPreview) join(); else preview();
        });
        binding.errorSecondary.setOnClickListener(v -> {
            if ("NETWORK".equals(errorScreen)) goHome();
            else if ("FULL".equals(errorScreen)) preview();
            else resetToCode();
        });
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
        if (!requestPending && showingPreview && errorScreen.isEmpty() && !isFinishing()) preview();
        if (!model.isAvailable()) binding.status.setText(R.string.join_unavailable);
        showOrigin();
        updateControls();

        // 링크로 들어왔으면 그대로 확인한다. 잘못된 링크면 preview()가 코드 칸에 오류를 보여 준다
        String invite = getIntent().getStringExtra(EXTRA_INVITE);
        if (saved == null && invite != null) {
            String code = MeetingRules.parseInviteCode(invite);
            binding.codeInput.setText(code != null ? code : invite);
            preview();
        }
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
            errorScreen = "";
            if (action.error != null) {
                String code = action.error.code;
                if ("MEETING_NOT_FOUND".equals(code) || "MEETING_EXPIRED".equals(code)) errorScreen = "GONE";
                else if ("MEETING_CLOSED".equals(code)) errorScreen = "CLOSED";
                else if ("MEETING_FULL".equals(code)) errorScreen = "FULL";
                else if (action.error.isNetwork() || action.error.httpStatus >= 500) errorScreen = "NETWORK";
            }
            // 검증·요청 제한 오류는 서버의 설명을 유지한다.
            binding.retryButton.setVisibility(errorScreen.isEmpty() && showingPreview ? View.VISIBLE : View.GONE);
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
        errorScreen = "";
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
        if (preview.status == Status.CONFIRMED) errorScreen = "CONFIRMED";
        else if (preview.status != Status.OPEN) errorScreen = "CLOSED";
        else if (!joinable) errorScreen = "FULL";
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
        if (!errorScreen.isEmpty()) {
            renderError();
            return;
        }
        binding.errorPanel.setVisibility(View.GONE);
        binding.errorSecondary.setVisibility(View.GONE);
        binding.status.setVisibility(View.VISIBLE);
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
        if (!errorScreen.isEmpty()) { resetToCode(); return; }
        if (!showingPreview) { finish(); return; }
        showingPreview = false;
        joinable = false;
        binding.status.setText("");
        binding.retryButton.setVisibility(View.GONE);
        updateControls();
    }

    /** 같은 초대를 다시 조회한다. 참여 요청을 자동 재전송하지 않는다. */
    private void renderError() {
        hideKeyboard();
        binding.codePanel.setVisibility(View.GONE);
        binding.previewPanel.setVisibility(View.GONE);
        binding.footerHint.setVisibility(View.GONE);
        binding.retryButton.setVisibility(View.GONE);
        binding.errorPanel.setVisibility(View.VISIBLE);
        binding.errorSecondary.setVisibility(View.VISIBLE);
        binding.status.setVisibility(busy ? View.VISIBLE : View.GONE);

        boolean network = "NETWORK".equals(errorScreen);
        boolean full = "FULL".equals(errorScreen);
        boolean confirmed = "CONFIRMED".equals(errorScreen);
        boolean closed = "CLOSED".equals(errorScreen);
        binding.errorTitle.setText(network ? "초대를\n불러오지 못했어요"
                : full ? "지금은 자리가\n다 찼어요"
                : confirmed ? "이미 확정된\n약속이에요"
                : closed ? "지금은 참여할 수\n없는 약속이에요" : "이 초대는\n사용할 수 없어요");
        binding.errorCopy.setText(network ? "인터넷 연결을 확인하고\n잠시 후 다시 시도해 주세요."
                : full ? "한 약속에는 최대 10명까지\n함께할 수 있어요."
                : confirmed ? "확정된 약속에는\n새로 참여할 수 없어요."
                : closed ? "확정되었거나 취소된 약속에는\n새로 참여할 수 없어요."
                : "기간이 지났거나 찾을 수 없는 약속이에요.\n친구에게 새 초대를 받아 주세요.");
        binding.errorHint.setText(network ? "연결이나 서버에 일시적인 문제가 있을 수 있어요."
                : full ? "방장에게 참가 인원을 확인해 주세요."
                : confirmed || closed ? "함께하기로 했다면 방장에게 확인해 주세요."
                : "다른 초대가 있다면 코드나 링크로 참여할 수 있어요.");
        binding.errorBadge.setText(full ? "10" : confirmed ? "✓" : "!");
        binding.errorIcon.setImageResource(network ? R.drawable.ic_invite_offline
                : full ? R.drawable.ic_invite_people
                : confirmed || closed ? R.drawable.ic_point_calendar : R.drawable.ic_invite_unavailable);
        binding.submitButton.setText(busy ? "확인 중…" : network ? "다시 시도" : "시작 화면으로");
        binding.submitButton.setEnabled(!busy);
        binding.submitButton.setAlpha(busy ? 0.45f : 1f);
        binding.errorSecondary.setText(network ? "시작 화면으로"
                : full ? "참여 가능 여부 다시 확인" : "다른 초대 입력하기");
        binding.errorSecondary.setEnabled(!busy);
        ViewCompat.setAccessibilityHeading(binding.errorTitle, true);
    }

    /** 오류에서 돌아와도 코드·이름·출발지 초안은 지우지 않는다. */
    private void resetToCode() {
        if (busy) return;
        errorScreen = "";
        showingPreview = false;
        joinable = false;
        binding.status.setText("");
        binding.retryButton.setVisibility(View.GONE);
        updateControls();
        binding.scroll.scrollTo(0, 0);
    }

    private void goHome() {
        if (busy) return;
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
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
        errorScreen = state.getString("errorScreen", "");
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
        state.putString("errorScreen", errorScreen);
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
