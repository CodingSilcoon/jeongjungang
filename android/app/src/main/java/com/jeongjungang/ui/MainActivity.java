package com.jeongjungang.ui;

import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.jeongjungang.R;
import com.jeongjungang.databinding.ActivityMainBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.jeongjungang.data.repository.MeetingRepository;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // 시작 화면과 세 가지 진입 메뉴를 준비한다.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 이전 Android 버전에서도 제목을 접근성 헤딩으로 표시한다.
        ViewCompat.setAccessibilityHeading(binding.startHeading, true);

        WindowCompat.getInsetsController(getWindow(), binding.getRoot())
                .setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), binding.getRoot())
                .setAppearanceLightNavigationBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        bindAction(binding.findPlaceButton, R.string.start_find, R.string.start_find_detail);
        bindAction(binding.createMeetingButton, R.string.start_create, R.string.start_create_detail);
        bindAction(binding.joinMeetingButton, R.string.start_join, R.string.start_join_detail);

        // 첫 번째 메뉴에서 출발지 입력 화면을 연다.
        binding.findPlaceButton.setOnClickListener(view ->
                startActivity(new Intent(this, OriginActivity.class)));

        binding.createMeetingButton.setOnClickListener(view -> openMeetings());
        binding.joinMeetingButton.setOnClickListener(view ->
                startActivity(new Intent(this, JoinMeetingActivity.class)));
    }

    // 기존 약속은 저장된 참가 자격으로 다시 열고, 새 약속 만들기도 제공한다.
    private void openMeetings() {
        List<String> ids = MeetingRepository.getInstance(this).myMeetingIds();
        if (ids.isEmpty()) {
            startActivity(new Intent(this, CreateMeetingActivity.class));
            return;
        }
        String[] choices = new String[ids.size() + 1];
        choices[0] = "새 약속 만들기";
        for (int i = 0; i < ids.size(); i++) {
            choices[i + 1] = getSharedPreferences("meeting_rooms", MODE_PRIVATE)
                    .getString(ids.get(i) + ".title", "참여 중인 약속 " + (i + 1));
        }
        new MaterialAlertDialogBuilder(this).setTitle("내 약속")
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) startActivity(new Intent(this, CreateMeetingActivity.class));
                    else startActivity(MeetingRoomActivity.createIntent(this, ids.get(which - 1), null, null));
                }).setNegativeButton("닫기", null).show();
    }

    // 카드 전체를 접근성 버튼으로 읽고, 미구현 메뉴에는 안내를 표시한다.
    private void bindAction(View action, int title, int detail) {
        action.setContentDescription(getString(title) + ", " + getString(detail));
        ViewCompat.setAccessibilityDelegate(action, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Button.class.getName());
            }
        });
        // 다음 화면이 준비되면 메뉴별 실제 이동 동작으로 교체한다.
        action.setOnClickListener(view -> Snackbar.make(view,
                getString(R.string.start_preparing, getString(title)), Snackbar.LENGTH_LONG).show());
    }
}
