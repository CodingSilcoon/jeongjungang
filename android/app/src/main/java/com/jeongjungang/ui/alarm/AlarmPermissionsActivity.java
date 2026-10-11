package com.jeongjungang.ui.alarm;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.jeongjungang.R;
import com.jeongjungang.alarm.AlarmPermissions;
import com.jeongjungang.databinding.ActivityAlarmPermissionsBinding;
import com.jeongjungang.databinding.ItemAlarmPermissionBinding;

/** 기기 권한만 확인한다. 동의·알람 예약·책임 알람 활성화는 변경하지 않는다. */
public class AlarmPermissionsActivity extends AppCompatActivity {

    private ActivityAlarmPermissionsBinding binding;
    private ItemAlarmPermissionBinding notifications;
    private ItemAlarmPermissionBinding exact;
    private ItemAlarmPermissionBinding fullscreen;
    private ItemAlarmPermissionBinding battery;

    private final ActivityResultLauncher<String> notificationRequest = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> refresh());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityAlarmPermissionsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsets();

        notifications = addRow("알림", "필수", "출발 알람과 친구의 미응답 알림을 받아요.",
                R.drawable.ic_alarm_bell, this::requestNotifications);
        exact = addRow("정확한 알람", "필수", "정해진 출발 시각에 알람을 울려요.",
                R.drawable.ic_point_calendar, () -> openSettings(AlarmPermissions.exactAlarmSettings(this)));
        fullscreen = addRow("전체 화면 알람", "권장", "잠금 화면에서도 알람을 크게 보여줘요.",
                R.drawable.ic_permission_expand, () -> openSettings(AlarmPermissions.fullScreenIntentSettings(this)));
        battery = addRow("배터리 제한도 확인해 주세요", "권장", "알람 지연을 줄이는 권장 설정이에요.",
                R.drawable.ic_permission_battery, () -> openSettings(AlarmPermissions.batteryOptimizationSettings()));
        battery.getRoot().setBackground(null);
        battery.permissionAction.setText("보기");
        battery.permissionAllowed.setText("확인됨");

        binding.backButton.setOnClickListener(v -> finish());
        binding.doneButton.setOnClickListener(v -> {
            setResult(AlarmPermissions.isReady(this) ? RESULT_OK : RESULT_CANCELED);
            finish();
        });
    }

    /** 설정 화면에서 돌아오거나 권한 요청이 끝나면 실제 기기 상태를 다시 읽는다. */
    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (notifications == null) return;
        showState(notifications, AlarmPermissions.hasNotificationPermission(this));
        showState(exact, AlarmPermissions.canScheduleExactAlarms(this));
        showState(fullscreen, AlarmPermissions.canUseFullScreenIntent(this));
        showState(battery, AlarmPermissions.isIgnoringBatteryOptimizations(this));
        binding.permissionHint.setText(AlarmPermissions.isReady(this)
                ? "필수 권한이 준비됐어요.\n권한 확인만으로 알람이 켜지지는 않아요."
                : "알림과 정확한 알람을 허용해야\n출발 알람을 예약할 수 있어요.");
    }

    /** 허용된 항목은 체크로, 미허용 항목은 설정 버튼으로 표시한다. */
    private void showState(ItemAlarmPermissionBinding row, boolean allowed) {
        row.permissionAllowed.setVisibility(allowed ? View.VISIBLE : View.GONE);
        row.permissionAction.setVisibility(allowed ? View.GONE : View.VISIBLE);
    }

    private ItemAlarmPermissionBinding addRow(String title, String requirement, String description,
                                              int icon, Runnable action) {
        ItemAlarmPermissionBinding row = ItemAlarmPermissionBinding.inflate(
                getLayoutInflater(), binding.permissionRows, false);
        row.permissionTitle.setText(title);
        row.permissionRequirement.setText(requirement);
        row.permissionDescription.setText(description);
        row.permissionIcon.setImageResource(icon);
        row.permissionAction.setContentDescription(title + " 설정하기");
        row.permissionAction.setOnClickListener(v -> action.run());

        // 보통 글꼴에서는 시안처럼 오른쪽에 배치하고, 큰 글꼴에서는 아래로 내려 둔다.
        if (getResources().getConfiguration().fontScale <= 1.2f) {
            row.getRoot().removeView(row.permissionAction);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.setMarginStart(Math.round(8 * getResources().getDisplayMetrics().density));
            row.permissionHead.addView(row.permissionAction, params);
        }
        binding.permissionRows.addView(row.getRoot());
        return row;
    }

    /** 최초 요청은 시스템 팝업, 재요청 불가·구버전의 차단은 앱 알림 설정으로 안내한다. */
    private void requestNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            openSettings(AlarmPermissions.notificationSettings(this));
            return;
        }
        boolean asked = getPreferences(MODE_PRIVATE).getBoolean("notificationRequested", false);
        if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            new MaterialAlertDialogBuilder(this).setTitle("알림 권한")
                    .setMessage("출발 알람과 친구의 미응답 알림을 표시하려면 알림 권한이 필요해요.")
                    .setPositiveButton("계속", (dialog, which) -> launchNotificationRequest())
                    .setNegativeButton("나중에", null).show();
        } else if (asked) {
            openSettings(AlarmPermissions.notificationSettings(this));
        } else {
            launchNotificationRequest();
        }
    }

    private void launchNotificationRequest() {
        getPreferences(MODE_PRIVATE).edit().putBoolean("notificationRequested", true).apply();
        notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    /** 제조사별 설정 화면이 없으면 앱 상세 설정으로 이동한다. */
    private void openSettings(Intent intent) {
        if (intent == null) { refresh(); return; }
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException | SecurityException ignored) {
                Toast.makeText(this, "휴대폰 설정에서 정중앙의 권한을 확인해 주세요.", Toast.LENGTH_LONG).show();
            }
        }
    }

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
