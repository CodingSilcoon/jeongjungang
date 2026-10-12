package com.jeongjungang.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.lifecycle.ViewModelProvider;
import com.jeongjungang.R;
import com.jeongjungang.data.remote.meeting.MeetingModels.Snapshot;
import com.jeongjungang.data.remote.meeting.MeetingModels.Status;
import com.jeongjungang.databinding.ActivityMyMeetingsBinding;
import com.jeongjungang.databinding.ItemMyMeetingBinding;
import com.jeongjungang.ui.meeting.MyMeetingsViewModel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 기기에 저장된 참가 자격으로 조회한 실제 약속 목록. */
public class MyMeetingsActivity extends AppCompatActivity {

    private ActivityMyMeetingsBinding binding;
    private MyMeetingsViewModel model;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMyMeetingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsets();
        model = new ViewModelProvider(this).get(MyMeetingsViewModel.class);
        binding.backButton.setOnClickListener(v -> finish());
        binding.createButton.setOnClickListener(v -> startActivity(new Intent(this, CreateMeetingActivity.class)));
        binding.joinButton.setOnClickListener(v -> startActivity(new Intent(this, JoinMeetingActivity.class)));
        binding.retryButton.setOnClickListener(v -> model.refresh());
        model.getState().observe(this, this::render);
    }

    /** 만들기·초대·탈퇴 화면에서 돌아오면 목록을 새로 조회한다. */
    @Override protected void onResume() {
        super.onResume();
        model.refresh();
    }

    private void render(MyMeetingsViewModel.State state) {
        binding.loading.setVisibility(state.loading ? View.VISIBLE : View.GONE);
        binding.retryButton.setVisibility(state.message != null ? View.VISIBLE : View.GONE);
        binding.retryButton.setEnabled(!state.loading);
        boolean empty = !state.loading && state.meetings.isEmpty();
        binding.emptyIcon.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.emptyTitle.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.emptyTitle.setText(state.message == null ? "아직 약속이 없어요" : "약속을 불러오지 못했어요");
        binding.message.setText(state.loading ? "약속을 불러오는 중이에요."
                : state.message != null ? state.message
                : state.meetings.isEmpty() ? "새 약속을 만들거나\n친구에게 받은 초대로 참여해 보세요." : "");
        binding.message.setVisibility(binding.message.length() == 0 ? View.GONE : View.VISIBLE);
        binding.meetingList.removeAllViews();
        long now = System.currentTimeMillis();
        // 가까운 약속, 날짜 미정, 아직 만료되지 않은 지난 약속 순으로 보여 준다.
        for (int group = 0; group < 3; group++) {
            boolean headingAdded = false;
            for (Snapshot snapshot : state.meetings) {
                Long time = snapshot.meeting.meetAtMillis;
                int category = time == null ? 1 : time >= now ? 0 : 2;
                if (category != group) continue;
                if (!headingAdded) {
                    addHeading(group == 0 ? "다가오는 약속" : group == 1 ? "날짜를 정하고 있어요" : "지난 약속");
                    headingAdded = true;
                }
                addMeeting(snapshot);
            }
        }
    }

    private void addHeading(String label) {
        TextView heading = new TextView(this);
        heading.setText(label);
        heading.setTextSize(12);
        heading.setTextColor(getColor(R.color.start_muted));
        heading.setPadding(0, dp(8), 0, dp(12));
        ViewCompat.setAccessibilityHeading(heading, true);
        binding.meetingList.addView(heading);
    }

    /** 확정 상태는 서버 값으로 판정한다. 카드 전체를 하나의 접근성 버튼으로 제공한다. */
    private void addMeeting(Snapshot snapshot) {
        ItemMyMeetingBinding card = ItemMyMeetingBinding.inflate(getLayoutInflater(), binding.meetingList, false);
        boolean confirmed = snapshot.meeting.status == Status.CONFIRMED && snapshot.meeting.place != null
                && snapshot.meeting.meetAtMillis != null;
        Long time = snapshot.meeting.meetAtMillis;
        String title = snapshot.meeting.title == null || snapshot.meeting.title.isEmpty() ? "우리 약속" : snapshot.meeting.title;
        card.date.setText(time == null ? "날짜 미정" : format(time, "M월 d일 · EEEE"));
        card.status.setText(confirmed ? "✓ 장소 확정" : snapshot.meeting.status == Status.UNKNOWN ? "상태 확인 필요" : "장소 정하는 중");
        if (!confirmed) {
            card.status.setBackgroundResource(R.drawable.start_here_background);
            card.status.setTextColor(getColor(R.color.start_ink));
        }
        card.title.setText(title);
        card.time.setText(time == null ? "날짜·시간 미정" : format(time, "a h:mm"));
        card.place.setText(snapshot.meeting.place == null ? "아직 장소를 고르고 있어요" : snapshot.meeting.place.name);
        card.people.setText(snapshot.members.size() + "명 참여" + (snapshot.amHost() ? " · 방장" : ""));
        card.getRoot().setContentDescription(title + ", " + card.date.getText() + ", " + card.time.getText()
                + ", " + card.place.getText() + ", " + card.status.getText() + ", " + card.people.getText() + ", 약속 열기");
        ViewCompat.setAccessibilityDelegate(card.getRoot(), new AccessibilityDelegateCompat() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Button.class.getName());
            }
        });
        card.getRoot().setOnClickListener(v -> {
            if (confirmed) startActivity(new Intent(this, ConfirmMeetingActivity.class)
                    .putExtra("meetingId", snapshot.meeting.id));
            else startActivity(MeetingRoomActivity.createIntent(this, snapshot.meeting.id, null, null));
        });
        binding.meetingList.addView(card.getRoot());
    }

    private String format(long time, String pattern) {
        return new SimpleDateFormat(pattern, Locale.KOREAN).format(new Date(time));
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void applyInsets() {
        WindowCompat.getInsetsController(getWindow(), binding.getRoot()).setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), binding.getRoot()).setAppearanceLightNavigationBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
    }
}
