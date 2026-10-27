package com.jeongjungang.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.lifecycle.ViewModelProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.jeongjungang.R;
import com.jeongjungang.data.remote.meeting.MeetingModels.*;
import com.jeongjungang.data.remote.meeting.MeetingRules;
import com.jeongjungang.data.remote.meeting.MeetingUpdate;
import com.jeongjungang.data.remote.meeting.ParticipantUpdate;
import com.jeongjungang.databinding.ActivityMeetingRoomBinding;
import com.jeongjungang.databinding.SheetOriginCriteriaBinding;
import com.jeongjungang.domain.model.LatLng;
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

/** API.md 6절의 실제 약속 상태와 권한을 사용하는 대기방. */
public class MeetingRoomActivity extends AppCompatActivity {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private ActivityMeetingRoomBinding binding;
    private MeetingViewModel model;
    private Snapshot snapshot;
    private String meetingId;
    private String code;
    private String inviteUrl;
    private boolean busy;
    private boolean healthy;
    private Origin pendingOrigin;
    private BottomSheetDialog managementDialog;
    private com.jeongjungang.databinding.SheetMeetingManagementBinding managementBinding;
    private boolean managementPending;

    private final ActivityResultLauncher<Intent> originPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() != RESULT_OK || data == null) return;
                pendingOrigin = new Origin(data.getStringExtra("label"),
                        new LatLng(data.getDoubleExtra("lat", 0), data.getDoubleExtra("lng", 0)));
                savePendingOrigin();
            });

    /** 토큰은 기존 암호화 저장소에 두고, 화면에는 약속 ID와 초대 정보만 전달한다. */
    public static Intent createIntent(Context context, String id, String code, String url) {
        return new Intent(context, MeetingRoomActivity.class).putExtra("meetingId", id)
                .putExtra("inviteCode", code).putExtra("inviteUrl", url);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        binding = ActivityMeetingRoomBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        model = new ViewModelProvider(this).get(MeetingViewModel.class);
        meetingId = getIntent().getStringExtra("meetingId");
        if (meetingId == null) { finish(); return; }
        if (state != null && state.containsKey("pendingLabel")) {
            pendingOrigin = new Origin(state.getString("pendingLabel"),
                    new LatLng(state.getDouble("pendingLat"), state.getDouble("pendingLng")));
        }

        SharedPreferences saved = getSharedPreferences("meeting_rooms", MODE_PRIVATE);
        code = getIntent().getStringExtra("inviteCode");
        inviteUrl = getIntent().getStringExtra("inviteUrl");
        if (code == null) code = saved.getString(meetingId + ".code", null);
        if (inviteUrl == null) inviteUrl = saved.getString(meetingId + ".url", null);
        saved.edit().putString(meetingId + ".code", code).putString(meetingId + ".url", inviteUrl).apply();

        WindowCompat.getInsetsController(getWindow(), binding.getRoot()).setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), binding.getRoot()).setAppearanceLightNavigationBars(true);
        ViewCompat.setAccessibilityHeading(binding.heading, true);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });

        binding.backButton.setOnClickListener(v -> finish());
        binding.menuButton.setOnClickListener(v -> showMenu());
        binding.editButton.setOnClickListener(v -> editMeeting());
        binding.retryButton.setOnClickListener(v -> model.refresh());
        binding.findButton.setOnClickListener(v -> findPlaces());
        binding.copyButton.setOnClickListener(v -> {
            if (!canAct() || code == null) return;
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("초대 코드", code));
            binding.status.setText("초대 코드를 복사했어.");
        });
        binding.shareButton.setOnClickListener(v -> {
            if (!canAct() || code == null) return;
            ExternalApps.share(this, "[정중앙] " + title(snapshot) + "\n초대 코드: " + code
                    + (inviteUrl == null ? "" : "\n" + inviteUrl));
        });

        model.getState().observe(this, this::render);
        model.getAction().observe(this, this::onAction);
        model.open(meetingId);
    }

    /** 화면이 보일 때만 기존 ViewModel의 4초 폴링을 실행한다. */
    @Override protected void onStart() {
        super.onStart();
        if (meetingId != null) model.startPolling();
    }

    @Override protected void onStop() {
        model.stopPolling();
        super.onStop();
    }

    private boolean canAct() { return snapshot != null && healthy && !busy && !isFinishing(); }

    // 검색에서 돌아올 때 상태 조회 중이면 선택한 출발지를 보관했다가 저장한다.
    private void savePendingOrigin() {
        if (pendingOrigin == null || !canAct()) return;
        Origin selected = pendingOrigin;
        pendingOrigin = null;
        model.updateMe(new ParticipantUpdate().origin(selected));
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (pendingOrigin != null) {
            state.putString("pendingLabel", pendingOrigin.label);
            state.putDouble("pendingLat", pendingOrigin.location.lat);
            state.putDouble("pendingLng", pendingOrigin.location.lng);
        }
    }

    private String title(Snapshot value) {
        return value.meeting.title == null || value.meeting.title.trim().isEmpty()
                ? "우리 약속" : value.meeting.title;
    }

    /** 갱신 실패 시 이전 내용은 남기되 변경과 추천은 재연결 후 허용한다. */
    private void render(MeetingState state) {
        snapshot = state.snapshot;
        healthy = state.status == MeetingState.Status.READY && state.message == null;
        binding.details.setVisibility(snapshot == null ? View.GONE : View.VISIBLE);
        binding.retryButton.setVisibility(state.status == MeetingState.Status.ERROR
                || state.message != null && snapshot != null ? View.VISIBLE : View.GONE);
        binding.status.setText(busy ? "처리하고 있어요…" : state.message != null ? state.message
                : state.status == MeetingState.Status.LOADING ? "약속을 불러오고 있어요…" : "");
        if (state.status == MeetingState.Status.GONE) {
            getSharedPreferences("meeting_rooms", MODE_PRIVATE).edit()
                    .remove(meetingId + ".code").remove(meetingId + ".url").remove(meetingId + ".title").apply();
        }
        binding.menuButton.setEnabled(canAct());
        binding.editButton.setEnabled(canAct());
        binding.findButton.setEnabled(false);
        binding.findButton.setAlpha(0.45f);
        binding.footerHint.setText("");
        if (snapshot == null) return;

        // 초대받아 들어온 사람에게 "약속 만들기"는 맞지 않아서 역할에 따라 제목을 바꾼다
        binding.heading.setText(snapshot.amHost() ? R.string.meeting_create_title : R.string.meeting_room_member_title);
        getSharedPreferences("meeting_rooms", MODE_PRIVATE).edit()
                .putString(meetingId + ".title", title(snapshot)).apply();
        binding.meetingTitle.setText(title(snapshot));
        String[] purposes = {"식사", "카페", "술자리", "기타"};
        String date = snapshot.meeting.meetAtMillis == null ? "날짜·시간 미정"
                : DateTimeFormatter.ofPattern("M월 d일 a h:mm", Locale.KOREAN)
                        .format(Instant.ofEpochMilli(snapshot.meeting.meetAtMillis).atZone(SEOUL));
        String place = snapshot.meeting.place == null ? "만날 장소는 아직 정하는 중"
                : snapshot.meeting.place.name;
        binding.metadata.setText(purposes[snapshot.meeting.purpose.ordinal()] + " · " + date + "\n" + place
                + (snapshot.meeting.status == Status.CONFIRMED ? " · 확정됨" : ""));
        binding.editButton.setVisibility(snapshot.amHost() ? View.VISIBLE : View.GONE);
        binding.menuButton.setText(snapshot.amHost() ? "관리" : "더보기");
        binding.invitePanel.setVisibility(snapshot.amHost() && code != null
                && snapshot.meeting.status == Status.OPEN ? View.VISIBLE : View.GONE);
        binding.inviteCode.setText(code);
        binding.shareButton.setText(inviteUrl == null ? "초대 코드 공유" : "초대 링크 공유");

        int count = snapshot.recommendParticipants().size();
        binding.peopleCount.setText(snapshot.members.size() + "명");
        binding.peopleCount.setContentDescription("참가자 " + snapshot.members.size() + "명");
        binding.progress.setText("출발지 " + count + " / " + snapshot.members.size());
        binding.people.removeAllViews();
        for (Member member : snapshot.members) addMember(member);
        boolean open = snapshot.meeting.status == Status.OPEN;
        boolean confirmed = snapshot.meeting.status == Status.CONFIRMED;
        boolean enabled = canAct() && (confirmed || open && count >= 2 && count <= 10);
        binding.findButton.setText(confirmed ? "확정된 약속 보기" : "만날 곳 찾기");
        binding.findButton.setEnabled(enabled);
        binding.findButton.setAlpha(enabled ? 1f : 0.45f);
        binding.footerHint.setText(!open ? "확정된 약속이야. 방장이 확정을 되돌리면 다시 찾을 수 있어."
                : count < 2 ? "출발지가 2곳 이상 입력되면 찾을 수 있어."
                : snapshot.membersWithoutOrigin() > 0 ? snapshot.membersWithoutOrigin() + "명의 출발지를 기다리는 중이야."
                : "모두 출발지를 입력했어. 만날 곳을 찾아보자.");
        savePendingOrigin();
    }

    /** 서버의 참가 순서를 유지하며 이름·역할·출발지·입력 상태를 한 행에 묶는다. */
    private void addMember(Member member) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(16), 0, dp(16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        row.setLayoutParams(params);
        boolean mine = member.id.equals(snapshot.myParticipantId);

        LinearLayout identity = new LinearLayout(this);
        identity.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.addView(identity, new LinearLayout.LayoutParams(-1, -2));
        TextView name = new TextView(this);
        name.setText(member.nickname);
        name.setTextSize(15);
        name.setTypeface(null, Typeface.BOLD);
        name.setTextColor(getColor(R.color.start_ink));
        identity.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        if (mine) addMemberBadge(identity, "나", true);
        if (member.role == Role.HOST) addMemberBadge(identity, "방장", false);

        LinearLayout location = new LinearLayout(this);
        location.setGravity(android.view.Gravity.CENTER_VERTICAL);
        location.setPadding(0, dp(8), 0, 0);
        row.addView(location, new LinearLayout.LayoutParams(-1, -2));
        TextView origin = new TextView(this);
        origin.setText(member.origin == null ? "출발지 입력 전" : member.origin.label);
        origin.setTextSize(13);
        origin.setTextColor(getColor(R.color.start_muted));
        origin.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_point_pin, 0, 0, 0);
        origin.setCompoundDrawablePadding(dp(4));
        location.addView(origin, new LinearLayout.LayoutParams(0, -2, 1));

        TextView state = new TextView(this);
        state.setText(member.origin == null ? "기다리는 중" : "입력 완료");
        state.setTextSize(12);
        state.setTextColor(getColor(member.origin == null ? R.color.start_muted : R.color.start_point));
        // 큰 글씨에서도 상태가 출발지와 겹치지 않도록 줄바꿈을 허용한다.
        state.setMaxWidth(dp(100));
        if (member.origin != null) {
            state.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_point_check, 0, 0, 0);
            state.setCompoundDrawablePadding(dp(4));
        }
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(-2, -2);
        stateParams.setMarginStart(dp(12));
        location.addView(state, stateParams);

        // 본인의 출발지 변경 동작은 유지하고 긴 이름과 겹치지 않게 아래에 둔다.
        if (mine) {
            AppCompatButton edit = new AppCompatButton(this);
            edit.setText(member.origin == null ? "출발지 입력" : "출발지 변경");
            edit.setMinHeight(dp(48));
            edit.setTextSize(12);
            edit.setTextColor(getColor(R.color.start_ink));
            android.util.TypedValue feedback = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, feedback, true);
            edit.setBackgroundResource(feedback.resourceId);
            edit.setEnabled(canAct());
            edit.setOnClickListener(v -> {
                if (canAct()) originPicker.launch(new Intent(this, OriginActivity.class)
                        .putExtra(OriginActivity.PICK_ORIGIN, true));
            });
            LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(-2, -2);
            editParams.gravity = android.view.Gravity.END;
            editParams.topMargin = dp(4);
            row.addView(edit, editParams);
        }
        binding.people.addView(row);

        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.start_muted));
        divider.setAlpha(0.2f);
        divider.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        binding.people.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    /** '나'만 레몬색으로 강조하고 방장 표시는 부드러운 바탕을 사용한다. */
    private void addMemberBadge(LinearLayout identity, String label, boolean mine) {
        TextView badge = new TextView(this);
        badge.setText(label);
        badge.setTextSize(12);
        badge.setTextColor(getColor(mine ? R.color.start_ink : R.color.start_muted));
        badge.setTypeface(null, mine ? Typeface.BOLD : Typeface.NORMAL);
        badge.setPadding(dp(6), dp(2), dp(6), dp(2));
        badge.setBackgroundResource(mine ? R.drawable.meeting_lemon_badge : R.drawable.meeting_role_badge);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(-2, -2);
        badgeParams.setMarginStart(dp(7));
        identity.addView(badge, badgeParams);
    }

    /** 추천을 시작한 시점의 참가자 목록을 고정해 확인한 대상이 바뀌지 않게 한다. */
    private void findPlaces() {
        if (canAct() && snapshot.meeting.status == Status.CONFIRMED) {
            startActivity(new Intent(this, ConfirmMeetingActivity.class).putExtra("meetingId", meetingId));
            return;
        }
        if (!canAct() || snapshot.meeting.status != Status.OPEN) return;
        Snapshot selected = snapshot;
        if (selected.recommendParticipants().size() < 2) return;
        List<String> missing = new ArrayList<>();
        for (Member member : selected.members) if (member.origin == null) missing.add(member.nickname);
        if (!missing.isEmpty()) {
            new MaterialAlertDialogBuilder(this).setTitle("입력한 사람들로 찾을까?")
                    .setMessage(String.join(", ", missing) + "은 출발지가 없어 이번 추천에서 빠져.")
                    .setNegativeButton("기다릴게", null)
                    .setPositiveButton("만날 곳 찾기", (d, w) -> chooseCriteria(selected)).show();
        } else chooseCriteria(selected);
    }

    private void chooseCriteria(Snapshot selected) {
        if (!canAct()) return;
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        SheetOriginCriteriaBinding sheet = SheetOriginCriteriaBinding.inflate(getLayoutInflater());
        dialog.setContentView(sheet.getRoot());
        sheet.confirmButton.setOnClickListener(v -> {
            dialog.dismiss();
            if (!canAct() || snapshot.meeting.status != Status.OPEN) return;
            if (snapshot.version != selected.version) {
                binding.status.setText("약속 정보가 바뀌었어. 참여자를 확인하고 다시 찾아줘.");
                return;
            }
            ArrayList<Bundle> origins = new ArrayList<>();
            for (Member member : selected.members) {
                if (member.origin == null) continue;
                Bundle item = new Bundle();
                item.putString("name", member.nickname);
                item.putString("label", member.origin.label);
                item.putDouble("lat", member.origin.location.lat);
                item.putDouble("lng", member.origin.location.lng);
                origins.add(item);
            }
            startActivity(new Intent(this, RecommendationActivity.class)
                    .putExtra("meetingId", meetingId)
                    .putExtra("meetingVersion", selected.version)
                    .putParcelableArrayListExtra("origins", origins)
                    .putExtra("fair", sheet.criteriaGroup.getCheckedRadioButtonId() == R.id.fairCriterion));
        });
        dialog.show();
    }

    /** 방장·참가자 메뉴를 기존 API 권한과 동일하게 구성한다. */
    private void showMenu() {
        if (!canAct()) return;
        openManagementSheet("약속 관리");
        sheetText(snapshot.amHost() ? "방장으로 참여 중" : "참가자로 참여 중");
        if (snapshot.amHost()) sheetButton("약속 수정 · 이름 / 목적 / 시간", false, this::editMeeting);
        sheetButton("내 이름 수정", false, this::editNickname);
        if (snapshot.amHost()) {
            sheetButton("참가자 관리", false, this::kickMember);
            if (snapshot.meeting.status == Status.CONFIRMED) {
                sheetButton("장소 다시 정하기", false, () -> confirmAction("장소를 다시 정할까요?",
                        "약속 확정을 되돌리고 대기방에서 만날 곳을 다시 찾습니다.", "확정 되돌리기",
                        true, () -> model.updateMeeting(new MeetingUpdate().reopen())));
            }
            AppCompatButton cancel = sheetButton("약속 취소", false, () -> confirmAction("약속을 취소할까요?",
                    "모든 참가자의 약속과 예약된 알람이 삭제됩니다. 취소한 약속은 되돌릴 수 없어요.",
                    "약속 취소하기", true, model::cancelMeeting));
            cancel.setTextColor(android.graphics.Color.rgb(161, 61, 50));
        }
        sheetButton("약속 나가기", false, this::confirmLeave);
        showManagementSheet();
    }

    /** 방장 승계와 혼자 남은 방장의 취소를 구분한다. */
    private void confirmLeave() {
        if (!canAct()) return;
        String message;
        if (!snapshot.amHost()) {
            message = "이 약속에서 나가고 내 기기에 예약된 알람을 삭제합니다. 다시 참여하려면 참여 가능한 초대가 필요해요.";
        } else if (snapshot.members.size() == 1) {
            message = "혼자 남은 방장이 나가면 약속이 취소되고 예약된 알람도 삭제됩니다.";
        } else {
            String next = "가장 먼저 들어온 참가자";
            for (Member member : snapshot.members) {
                if (!member.id.equals(snapshot.myParticipantId)) { next = member.nickname + "님"; break; }
            }
            message = next + "에게 방장이 넘어갑니다. 내 기기에 예약된 이 약속의 알람도 삭제됩니다.";
        }
        confirmAction("약속에서 나갈까요?", message, "약속 나가기", false, model::leave);
    }

    private void kickMember() {
        if (!canAct() || !snapshot.amHost()) return;
        openManagementSheet("참가자 관리");
        boolean found = false;
        for (Member member : snapshot.members) {
            if (member.id.equals(snapshot.myParticipantId)) continue;
            found = true;
            sheetButton(member.nickname + " · 내보내기", false, () ->
                    confirmAction(member.nickname + "님을 내보낼까요?",
                            "이 참가자는 약속에서 제외됩니다. 다시 참여하려면 참여 가능한 초대가 필요해요.",
                            "내보내기", true, () -> model.kick(member.id)));
        }
        if (!found) sheetText("다른 참가자가 없어요.");
        sheetButton("돌아가기", false, this::showMenu);
        showManagementSheet();
    }

    /** 확인 중 참가자 구성이 바뀌면 영향 설명을 다시 읽도록 한다. */
    private void confirmAction(String title, String message, String label, boolean hostOnly, Runnable action) {
        if (!canAct() || hostOnly && !snapshot.amHost()) return;
        long version = snapshot.version;
        openManagementSheet(title);
        sheetText(message);
        sheetButton(label, true, () -> {
            if (!sheetCanAct(hostOnly)) return;
            if (snapshot.version != version) {
                managementBinding.sheetError.setText("약속 정보가 바뀌었어요. 돌아가서 다시 확인해 주세요.");
                return;
            }
            managementPending = true;
            action.run();
        });
        sheetButton("돌아가기", false, this::showMenu);
        showManagementSheet();
    }

    /** 날짜 선택은 초안에만 반영한다. 저장 버튼을 눌러야 서버를 변경한다. */
    private void editMeeting() {
        if (!canAct() || !snapshot.amHost()) return;
        Meeting original = snapshot.meeting;
        openManagementSheet("약속 수정");
        sheetText("약속 이름");
        EditText name = sheetInput(original.title);
        sheetText("목적");
        android.widget.Spinner purpose = new android.widget.Spinner(this);
        purpose.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"식사", "카페", "술자리", "기타"}));
        purpose.setSelection(original.purpose.ordinal());
        managementBinding.sheetContent.addView(purpose, new LinearLayout.LayoutParams(-1, dp(48)));
        Long[] selectedTime = {original.meetAtMillis};
        AppCompatButton date = sheetButton(meetingDate(selectedTime[0]), false, () -> {});
        date.setContentDescription("약속 날짜와 시간 선택");
        date.setOnClickListener(v -> {
            ZonedDateTime initial = selectedTime[0] == null ? ZonedDateTime.now(SEOUL)
                    : Instant.ofEpochMilli(selectedTime[0]).atZone(SEOUL);
            new DatePickerDialog(this, (picker, year, month, day) ->
                    new TimePickerDialog(this, (clock, hour, minute) -> {
                        selectedTime[0] = LocalDateTime.of(year, month + 1, day, hour, minute)
                                .atZone(SEOUL).toInstant().toEpochMilli();
                        date.setText(meetingDate(selectedTime[0]));
                    }, initial.getHour(), initial.getMinute(), true).show(),
                    initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
        });
        sheetText("시간을 바꾸면 책임 알람이 켜진 약속의 참가자별 알람 시각도 다시 계산됩니다.");
        sheetButton("변경 내용 저장", true, () -> {
            if (!sheetCanAct(true)) return;
            if (!java.util.Objects.equals(original.title, snapshot.meeting.title)
                    || !java.util.Objects.equals(original.meetAtMillis, snapshot.meeting.meetAtMillis)
                    || original.purpose != snapshot.meeting.purpose) {
                managementBinding.sheetError.setText("다른 곳에서 약속이 수정됐어요. 다시 열어 확인해 주세요.");
                return;
            }
            String title = name.getText().toString().trim();
            String error = MeetingRules.checkTitle(title);
            if (error != null) { name.setError(error); return; }
            MeetingUpdate update = new MeetingUpdate();
            if (!java.util.Objects.equals(title, original.title == null ? "" : original.title)) update.title(title);
            Purpose chosen = Purpose.values()[purpose.getSelectedItemPosition()];
            if (chosen != original.purpose) update.purpose(chosen);
            if (!java.util.Objects.equals(selectedTime[0], original.meetAtMillis) && selectedTime[0] != null) {
                error = MeetingRules.checkMeetAt(selectedTime[0], System.currentTimeMillis());
                if (error != null) { managementBinding.sheetError.setText(error); return; }
                update.meetAt(selectedTime[0]);
            }
            if (update.isEmpty()) { managementDialog.dismiss(); return; }
            managementPending = true;
            model.updateMeeting(update);
        });
        sheetButton("돌아가기", false, this::showMenu);
        showManagementSheet();
    }

    private void editNickname() {
        if (!canAct() || snapshot.me() == null) return;
        openManagementSheet("내 이름 수정");
        sheetText("이 약속에서 사용할 이름");
        EditText name = sheetInput(snapshot.me().nickname);
        sheetButton("저장하기", true, () -> {
            if (!sheetCanAct(false)) return;
            String value = name.getText().toString().trim();
            String error = MeetingRules.checkNickname(value);
            if (error != null) { name.setError(error); return; }
            managementPending = true;
            model.updateMe(new ParticipantUpdate().nickname(value));
        });
        sheetButton("돌아가기", false, this::showMenu);
        showManagementSheet();
    }

    /** 저장 직전에 최신 역할과 연결 상태를 다시 확인한다. */
    private boolean sheetCanAct(boolean hostOnly) {
        if (canAct() && (!hostOnly || snapshot.amHost())) return true;
        if (managementBinding != null) managementBinding.sheetError.setText("권한이나 연결 상태가 바뀌었어요. 다시 확인해 주세요.");
        return false;
    }

    private String meetingDate(Long time) {
        return time == null ? "약속 날짜와 시간 선택" : DateTimeFormatter.ofPattern("M월 d일 a h:mm", Locale.KOREAN)
                .format(Instant.ofEpochMilli(time).atZone(SEOUL));
    }

    /** 같은 색감의 스크롤 가능한 하단 팝업을 모든 관리 동작에 재사용한다. */
    private void openManagementSheet(String title) {
        if (managementDialog != null) managementDialog.dismiss();
        managementPending = false;
        managementDialog = new BottomSheetDialog(this);
        managementBinding = com.jeongjungang.databinding.SheetMeetingManagementBinding.inflate(getLayoutInflater());
        managementDialog.setContentView(managementBinding.getRoot());
        managementBinding.sheetTitle.setText(title);
        ViewCompat.setAccessibilityHeading(managementBinding.sheetTitle, true);
        managementBinding.closeButton.setOnClickListener(v -> { if (!busy) managementDialog.dismiss(); });
    }

    private void showManagementSheet() {
        managementDialog.show();
        managementDialog.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        if (managementDialog.getWindow() != null) managementDialog.getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        managementBinding.getRoot().post(() -> {
            if (managementBinding == null || !managementDialog.isShowing()) return;
            int limit = Math.max(dp(120), getResources().getDisplayMetrics().heightPixels * 7 / 10 - dp(130));
            if (managementBinding.sheetContent.getHeight() > limit) {
                managementBinding.sheetScroll.getLayoutParams().height = limit;
                managementBinding.sheetScroll.requestLayout();
            }
        });
    }

    private void sheetText(String value) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(13);
        text.setTextColor(getColor(R.color.start_muted));
        text.setLineSpacing(dp(4), 1);
        text.setPadding(0, dp(8), 0, dp(14));
        managementBinding.sheetContent.addView(text);
    }

    private EditText sheetInput(String value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(value);
        input.setContentDescription(managementBinding.sheetTitle.getText());
        input.setTextSize(16);
        input.setTextColor(getColor(R.color.start_ink));
        input.setMinHeight(dp(48));
        managementBinding.sheetContent.addView(input, new LinearLayout.LayoutParams(-1, -2));
        return input;
    }

    private AppCompatButton sheetButton(String label, boolean primary, Runnable action) {
        AppCompatButton button = new AppCompatButton(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(primary ? 16 : 14);
        button.setTypeface(null, Typeface.BOLD);
        button.setTextColor(getColor(R.color.start_ink));
        button.setSupportBackgroundTintList(null);
        button.setBackgroundResource(primary ? R.drawable.nearby_primary_background : R.drawable.start_action_secondary);
        button.setMinHeight(dp(primary ? 58 : 48));
        button.setPadding(dp(14), dp(12), dp(14), dp(12));
        if (!primary) {
            int icon = label.startsWith("약속 수정") ? R.drawable.ic_point_calendar
                    : label.startsWith("내 이름") || label.startsWith("참가자") ? R.drawable.ic_alarm_person : 0;
            button.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, R.drawable.start_chevron, 0);
            button.setCompoundDrawablePadding(dp(8));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        managementBinding.sheetContent.addView(button, params);
        button.setOnClickListener(v -> { if (sheetCanAct(false)) action.run(); });
        return button;
    }

    /** 통신 중에는 중복 저장과 팝업 닫기를 막고, 실패하면 입력을 유지한다. */
    private void updateManagementPending(ActionResult action) {
        if (managementDialog == null || !managementDialog.isShowing() || !managementPending) return;
        setSheetEnabled(managementBinding.getRoot(), !busy);
        managementDialog.setCancelable(!busy);
        managementDialog.setCanceledOnTouchOutside(!busy);
        managementDialog.getBehavior().setDraggable(!busy);
        managementBinding.sheetError.setText(busy ? "처리 중입니다…" : action.message == null ? "" : action.message);
        if (!busy) {
            managementPending = false;
            if (action.status == ActionResult.Status.DONE) managementDialog.dismiss();
        }
    }

    private void setSheetEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) setSheetEnabled(group.getChildAt(i), enabled);
        }
    }

    @Override protected void onDestroy() {
        if (managementDialog != null) managementDialog.dismiss();
        super.onDestroy();
    }

    /** 완료되기 전 중복 변경을 막고, 나가기·취소 성공 후에만 화면을 닫는다. */
    private void onAction(ActionResult action) {
        if (action == null) return;
        busy = action.status == ActionResult.Status.RUNNING;
        updateManagementPending(action);
        MeetingState state = model.getState().getValue();
        if (state != null) render(state);
        if (busy) return;
        if (action.status == ActionResult.Status.DONE
                && (action.kind == ActionResult.Kind.LEAVE || action.kind == ActionResult.Kind.CANCEL)) {
            getSharedPreferences("meeting_rooms", MODE_PRIVATE).edit().remove(meetingId + ".code")
                    .remove(meetingId + ".url").remove(meetingId + ".title").apply();
            finish();
        } else binding.status.setText(action.status == ActionResult.Status.FAILED ? action.message : "저장했어.");
        model.consumeAction();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
