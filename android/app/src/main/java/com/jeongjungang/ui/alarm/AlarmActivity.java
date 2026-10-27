package com.jeongjungang.ui.alarm;

import android.content.Context;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.format.DateFormat;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.jeongjungang.R;
import com.jeongjungang.alarm.AlarmRingService;
import com.jeongjungang.alarm.AlarmScheduler;
import com.jeongjungang.alarm.AlarmSpec;
import com.jeongjungang.data.remote.meeting.MeetingModels.Snapshot;
import com.jeongjungang.databinding.ActivityAlarmBinding;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 알람이 울릴 때 잠금 화면 위에 뜨는 전체 화면. 끄기 버튼 하나만 둔다.
 * 다른 곳에서 알람이 꺼지면(알림의 끄기 버튼, 시간 초과) 스스로 닫힌다.
 */
public class AlarmActivity extends AppCompatActivity {

    private static final String EXTRA_ALARM_ID = "alarmId";
    private static final String EXTRA_NICKNAME = "sleeperNickname";
    private static final String EXTRA_MEETING_ID = "meetingId";

    private ActivityAlarmBinding binding;
    private String alarmId;
    private AlarmMeetingViewModel model;
    private ObjectAnimator bellMotion;
    private final Handler clock = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = this::showClock;

    public static Intent intent(Context context, String alarmId) {
        return new Intent(context, AlarmActivity.class)
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
    }

    /** 에스컬레이션 화면. key는 {@link AlarmRingService#ringingAlarmId()} 값. */
    public static Intent escalationIntent(Context context, String key, String sleeperNickname) {
        return intent(context, key).putExtra(EXTRA_NICKNAME, sleeperNickname);
    }

    /** 푸시에 있는 약속 ID로 화면 표시용 상세 정보를 조회한다. */
    public static Intent escalationIntent(Context context, String key, String nickname, String meetingId) {
        return escalationIntent(context, key, nickname).putExtra(EXTRA_MEETING_ID, meetingId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        showOverLockScreen();
        binding = ActivityAlarmBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsets();
        model = new ViewModelProvider(this).get(AlarmMeetingViewModel.class);
        bind(getIntent());
        model.getSnapshot().observe(this, this::showMeeting);
        model.isLoading().observe(this, loading -> binding.meetingUnavailable.setText(
                Boolean.TRUE.equals(loading) ? R.string.alarm_meeting_loading : R.string.alarm_meeting_unavailable));

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
        stopBellMotion();
        alarmId = intent.getStringExtra(EXTRA_ALARM_ID);
        binding.meetingCard.setVisibility(View.GONE);
        binding.meetingUnavailable.setVisibility(View.VISIBLE);
        if (AlarmRingService.isEscalationKey(alarmId)) {
            String nickname = intent.getStringExtra(EXTRA_NICKNAME);
            if (nickname == null || nickname.trim().isEmpty()) nickname = getString(R.string.alarm_escalation_someone);
            SpannableString title = new SpannableString(getString(R.string.alarm_escalation_title, nickname));
            title.setSpan(new ForegroundColorSpan(getColor(R.color.start_point)),
                    0, nickname.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            binding.alarmTitle.setText(title);
            binding.alarmKind.setText(R.string.alarm_friend_kind);
            binding.alarmAvatar.setImageResource(R.drawable.ic_alarm_person);
            binding.alarmBell.setVisibility(View.VISIBLE);
            binding.alarmMessage.setText(R.string.alarm_escalation_message);
            binding.dismissButton.setText(R.string.alarm_escalation_dismiss);
            model.load(intent.getStringExtra(EXTRA_MEETING_ID));
            startBellMotion();
            return;
        }
        model.load(null);
        AlarmSpec spec = alarmId == null ? null : new AlarmScheduler(this).find(alarmId);
        binding.alarmTitle.setText(spec != null ? spec.title : "출발할 시간이에요");
        binding.alarmKind.setText(R.string.alarm_departure_kind);
        binding.alarmAvatar.setImageResource(R.drawable.ic_alarm_bell);
        binding.alarmBell.setVisibility(View.GONE);
        if (spec != null) showDetails(spec.title, spec.place, spec.meetAtMillis);
        binding.alarmMessage.setText(com.jeongjungang.R.string.alarm_depart_now);
        binding.dismissButton.setText(com.jeongjungang.R.string.alarm_dismiss);
    }

    /** 조회가 실패해도 기본 안내와 알람 끄기 버튼을 유지한다. */
    private void showMeeting(Snapshot snapshot) {
        if (!AlarmRingService.isEscalationKey(alarmId) || snapshot == null
                || !snapshot.meeting.id.equals(getIntent().getStringExtra(EXTRA_MEETING_ID))) return;

        showDetails(snapshot.meeting.title,
                snapshot.meeting.place == null ? null : snapshot.meeting.place.name,
                snapshot.meeting.meetAtMillis == null ? 0 : snapshot.meeting.meetAtMillis);
    }

    /** 예시 값 없이 저장된 약속의 날짜·장소를 표시한다. */
    private void showDetails(String title, String place, long meetAt) {
        binding.meetingCard.setVisibility(View.VISIBLE);
        binding.meetingUnavailable.setVisibility(View.GONE);
        binding.meetingTitle.setText(title);
        binding.meetingTitle.setVisibility(title == null || title.isEmpty() ? View.GONE : View.VISIBLE);
        binding.meetingPlace.setText(place == null || place.isEmpty() ? getString(R.string.alarm_place_unknown) : place);
        binding.alarmSubtitle.setText(meetAt <= 0 ? getString(R.string.alarm_time_unknown)
                : getString(R.string.alarm_meeting_time,
                    new SimpleDateFormat("M월 d일 a h:mm", Locale.KOREAN).format(new Date(meetAt))));
    }

    /** 시스템 바와 화면 잘림 영역만큼 여백을 확보한다. */
    private void applyInsets() {
        WindowCompat.getInsetsController(getWindow(), binding.getRoot()).setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), binding.getRoot()).setAppearanceLightNavigationBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
    }

    /** 종만 짧게 세 번 흔든다. 시스템에서 애니메이션을 끄면 정지한다. */
    private void startBellMotion() {
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        bellMotion = ObjectAnimator.ofFloat(binding.alarmBell, View.ROTATION, 0, -8, 8, -6, 4, -2, 0, 0);
        bellMotion.setDuration(800);
        bellMotion.setRepeatCount(2);
        bellMotion.start();
    }

    private void stopBellMotion() {
        if (bellMotion != null) {
            bellMotion.cancel();
            bellMotion = null;
        }
        if (binding != null) binding.alarmBell.setRotation(0);
    }

    /** 시계는 기기 언어와 상관없이 한국어로 보여 준다. 24시간제 설정은 따른다. */
    private void showClock() {
        Date now = new Date();
        binding.alarmDate.setText(new SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN).format(now));
        binding.alarmClock.setText(new SimpleDateFormat(
                DateFormat.is24HourFormat(this) ? "HH:mm" : "a h:mm", Locale.KOREAN).format(now));
        // 다음 분이 바뀌는 순간에 다시 그린다
        long untilNextMinute = 60_000 - System.currentTimeMillis() % 60_000;
        clock.removeCallbacks(clockTick);
        clock.postDelayed(clockTick, untilNextMinute);
    }

    @Override
    protected void onStart() {
        super.onStart();
        showClock();
    }

    @Override
    protected void onStop() {
        clock.removeCallbacks(clockTick);
        stopBellMotion();
        super.onStop();
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
