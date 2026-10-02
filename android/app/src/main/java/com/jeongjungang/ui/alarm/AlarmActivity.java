package com.jeongjungang.ui.alarm;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import com.jeongjungang.alarm.AlarmRingService;
import com.jeongjungang.alarm.AlarmScheduler;
import com.jeongjungang.alarm.AlarmSpec;
import com.jeongjungang.alarm.AlarmTexts;
import com.jeongjungang.databinding.ActivityAlarmBinding;

/**
 * 알람이 울릴 때 잠금 화면 위에 뜨는 전체 화면. 끄기 버튼 하나만 둔다.
 * 다른 곳에서 알람이 꺼지면(알림의 끄기 버튼, 시간 초과) 스스로 닫힌다.
 */
public class AlarmActivity extends AppCompatActivity {

    private static final String EXTRA_ALARM_ID = "alarmId";

    private ActivityAlarmBinding binding;
    private String alarmId;

    public static Intent intent(Context context, String alarmId) {
        return new Intent(context, AlarmActivity.class)
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showOverLockScreen();
        binding = ActivityAlarmBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        bind(getIntent());

        binding.dismissButton.setOnClickListener(v -> {
            startService(AlarmRingService.dismissIntent(this, alarmId));
            finish();
        });
        AlarmRingService.ringingAlarmId().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String ringing) {
                if (alarmId != null && !alarmId.equals(ringing)) {
                    finish();
                }
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        bind(intent);
    }

    private void bind(Intent intent) {
        alarmId = intent.getStringExtra(EXTRA_ALARM_ID);
        AlarmSpec spec = alarmId == null ? null : new AlarmScheduler(this).find(alarmId);
        binding.alarmTitle.setText(spec != null ? spec.title : "출발할 시간이에요");
        binding.alarmSubtitle.setText(spec != null ? AlarmTexts.subtitle(spec) : "");
    }

    private void showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}
