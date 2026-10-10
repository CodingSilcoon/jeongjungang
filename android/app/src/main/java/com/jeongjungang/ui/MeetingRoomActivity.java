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
        binding.peopleHeading.setText("함께 만날 사람 " + snapshot.members.size());
        binding.progress.setText("출발지 " + count + " / " + snapshot.members.size());
        binding.people.removeAllViews();
        for (Member member : snapshot.members) addMember(member);
        boolean open = snapshot.meeting.status == Status.OPEN;
        boolean enabled = canAct() && open && count >= 2 && count <= 10;
        binding.findButton.setEnabled(enabled);
        binding.findButton.setAlpha(enabled ? 1f : 0.45f);
        binding.footerHint.setText(!open ? "확정된 약속이야. 방장이 확정을 되돌리면 다시 찾을 수 있어."
                : count < 2 ? "출발지가 2곳 이상 입력되면 찾을 수 있어."
                : snapshot.membersWithoutOrigin() > 0 ? snapshot.membersWithoutOrigin() + "명의 출발지를 기다리는 중이야."
                : "모두 출발지를 입력했어. 만날 곳을 찾아보자.");
        savePendingOrigin();
    }

    /** 이름·출발지는 서버 값, 변경 버튼은 본인 행에만 표시한다. */
    private void addMember(Member member) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12);
        row.setLayoutParams(params);
        boolean mine = member.id.equals(snapshot.myParticipantId);
        TextView avatar = new TextView(this);
        avatar.setText(member.nickname.isEmpty() ? "?" : member.nickname.substring(0,
                member.nickname.offsetByCodePoints(0, 1)));
        avatar.setGravity(android.view.Gravity.CENTER);
        avatar.setTextColor(getColor(R.color.start_ink));
        avatar.setBackgroundResource(mine ? R.drawable.start_action_primary : R.drawable.start_action_secondary);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(40), dp(44));
        avatarParams.setMarginEnd(dp(12));
        row.addView(avatar, avatarParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView name = new TextView(this);
        name.setText(member.nickname + (mine ? " · 나" : "") + (member.role == Role.HOST ? " · 방장" : ""));
        name.setTextSize(15);
        name.setTypeface(null, Typeface.BOLD);
        name.setTextColor(getColor(R.color.start_ink));
        labels.addView(name);
        TextView origin = new TextView(this);
        origin.setText(member.origin == null ? "출발지 입력 전 · 대기" : member.origin.label);
        origin.setTextSize(13);
        origin.setTextColor(getColor(R.color.start_muted));
        origin.setPadding(0, dp(8), 0, 0);
        labels.addView(origin);
        if (mine) {
            AppCompatButton edit = new AppCompatButton(this);
            edit.setText(member.origin == null ? "출발지 입력" : "출발지 변경");
            edit.setMinHeight(dp(48));
            edit.setTextSize(12);
            edit.setTextColor(getColor(R.color.start_ink));
            edit.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            edit.setEnabled(canAct());
            edit.setOnClickListener(v -> {
                if (canAct()) originPicker.launch(new Intent(this, OriginActivity.class)
                        .putExtra(OriginActivity.PICK_ORIGIN, true));
            });
            row.addView(edit, new LinearLayout.LayoutParams(dp(84), -2));
        }
        binding.people.addView(row);
    }

    /** 추천을 시작한 시점의 참가자 목록을 고정해 확인한 대상이 바뀌지 않게 한다. */
    private void findPlaces() {
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
                    .putParcelableArrayListExtra("origins", origins)
                    .putExtra("fair", sheet.criteriaGroup.getCheckedRadioButtonId() == R.id.fairCriterion));
        });
        dialog.show();
    }

    /** 관리 기능은 서버 권한과 동일하게 방장/본인으로 나눈다. */
    private void showMenu() {
        if (!canAct()) return;
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add("내 이름 수정");
        actions.add(() -> editText("내 이름", snapshot.me() == null ? "" : snapshot.me().nickname, false));
        if (snapshot.amHost()) {
            labels.add("참가자 내보내기"); actions.add(this::kickMember);
            labels.add("약속 취소"); actions.add(() -> confirmAction("약속을 취소할까?",
                    "모든 참가자의 약속과 알람이 삭제돼.", () -> { if (snapshot.amHost()) model.cancelMeeting(); }));
            if (snapshot.meeting.status == Status.CONFIRMED) {
                labels.add("확정 되돌리기");
                actions.add(() -> confirmAction("확정을 되돌릴까?", "만날 곳을 다시 찾을 수 있어.",
                        () -> { if (snapshot.amHost()) model.updateMeeting(new MeetingUpdate().reopen()); }));
            }
        }
        labels.add("약속 나가기");
        actions.add(() -> confirmAction("약속에서 나갈까?", snapshot.amHost()
                ? snapshot.members.size() == 1 ? "혼자 남은 방장이 나가면 약속이 취소돼."
                : "가장 먼저 들어온 참가자에게 방장이 넘어가."
                : "다시 참여하려면 초대 코드가 필요해.", model::leave));
        new MaterialAlertDialogBuilder(this).setTitle("약속 메뉴")
                .setItems(labels.toArray(new String[0]), (d, w) -> { if (canAct()) actions.get(w).run(); })
                .setNegativeButton("닫기", null).show();
    }

    private void kickMember() {
        if (!canAct() || !snapshot.amHost()) return;
        List<Member> others = new ArrayList<>();
        for (Member member : snapshot.members) if (!member.id.equals(snapshot.myParticipantId)) others.add(member);
        String[] names = new String[others.size()];
        for (int i = 0; i < names.length; i++) names[i] = others.get(i).nickname;
        new MaterialAlertDialogBuilder(this).setTitle(names.length == 0 ? "다른 참가자가 없어." : "내보낼 참가자")
                .setItems(names, (d, w) -> confirmAction(names[w] + "을 내보낼까?", "다시 참여하려면 초대가 필요해.",
                        () -> { if (snapshot.amHost()) model.kick(others.get(w).id); }))
                .setNegativeButton("닫기", null).show();
    }

    private void confirmAction(String title, String message, Runnable action) {
        new MaterialAlertDialogBuilder(this).setTitle(title).setMessage(message)
                .setNegativeButton("취소", null)
                .setPositiveButton("확인", (d, w) -> { if (canAct()) action.run(); }).show();
    }

    private void editMeeting() {
        if (!canAct() || !snapshot.amHost()) return;
        new MaterialAlertDialogBuilder(this).setTitle("약속 수정")
                .setItems(new String[]{"약속 이름", "목적", "날짜와 시간"}, (d, w) -> {
                    if (!canAct() || !snapshot.amHost()) return;
                    if (w == 0) editText("약속 이름", snapshot.meeting.title, true);
                    if (w == 1) new MaterialAlertDialogBuilder(this).setTitle("목적")
                            .setItems(new String[]{"식사", "카페", "술자리", "기타"}, (dialog, index) -> {
                                if (canAct() && snapshot.amHost()) model.updateMeeting(
                                        new MeetingUpdate().purpose(Purpose.values()[index]));
                            }).show();
                    if (w == 2) pickDate();
                }).setNegativeButton("닫기", null).show();
    }

    /** 잘못된 입력은 대화상자를 닫지 않고 해당 입력칸에서 설명한다. */
    private void editText(String title, String current, boolean meetingTitle) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(current);
        var dialog = new MaterialAlertDialogBuilder(this).setTitle(title).setView(input)
                .setNegativeButton("취소", null).setPositiveButton("저장", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            if (!canAct() || meetingTitle && !snapshot.amHost()) { dialog.dismiss(); return; }
            String value = input.getText().toString().trim();
            String error = meetingTitle ? MeetingRules.checkTitle(value) : MeetingRules.checkNickname(value);
            if (error != null) { input.setError(error); return; }
            if (meetingTitle) model.updateMeeting(new MeetingUpdate().title(value));
            else model.updateMe(new ParticipantUpdate().nickname(value));
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void pickDate() {
        ZonedDateTime initial = snapshot.meeting.meetAtMillis == null ? ZonedDateTime.now(SEOUL)
                : Instant.ofEpochMilli(snapshot.meeting.meetAtMillis).atZone(SEOUL);
        new DatePickerDialog(this, (picker, year, month, day) ->
                new TimePickerDialog(this, (clock, hour, minute) -> {
                    if (canAct() && snapshot.amHost()) model.updateMeeting(new MeetingUpdate().meetAt(
                            LocalDateTime.of(year, month + 1, day, hour, minute).atZone(SEOUL).toInstant().toEpochMilli()));
                }, initial.getHour(), initial.getMinute(), true).show(),
                initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
    }

    /** 완료되기 전 중복 변경을 막고, 나가기·취소 성공 후에만 화면을 닫는다. */
    private void onAction(ActionResult action) {
        if (action == null) return;
        busy = action.status == ActionResult.Status.RUNNING;
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
