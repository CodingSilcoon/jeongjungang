package com.jeongjungang.ui.meeting;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.jeongjungang.data.remote.ApiException;
import com.jeongjungang.data.remote.meeting.MeetingModels.Snapshot;
import com.jeongjungang.data.repository.AccountabilityRepository;
import com.jeongjungang.data.repository.MeetingRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 이 기기의 참가 기록으로 각 약속을 조회한다. 일시적인 실패로 참가 기록을 삭제하지 않는다. */
public class MyMeetingsViewModel extends AndroidViewModel {

    public static final class State {
        public final List<Snapshot> meetings;
        public final boolean loading;
        public final String message;

        State(List<Snapshot> meetings, boolean loading, String message) {
            this.meetings = Collections.unmodifiableList(new ArrayList<>(meetings));
            this.loading = loading;
            this.message = message;
        }
    }

    private final MeetingRepository repository;
    private final AccountabilityRepository alarms;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(Collections.emptyList(), false, null));
    private boolean cleared;
    private boolean fetching;
    private long retryAt;

    public MyMeetingsViewModel(Application application) {
        super(application);
        repository = MeetingRepository.getInstance(application);
        alarms = AccountabilityRepository.getInstance(application);
    }

    public LiveData<State> getState() { return state; }

    /** 화면 복귀와 재시도에서 조회한다. 중복 호출과 서버의 재시도 대기시간을 지킨다. */
    public void refresh() {
        if (fetching || android.os.SystemClock.uptimeMillis() < retryAt) return;
        fetching = true;
        state.setValue(new State(Collections.emptyList(), true, null));
        executor.execute(() -> {
            List<Snapshot> loaded = new ArrayList<>();
            List<String> ids = repository.myMeetingIds();
            int failed = 0;
            long retryDelay = 0;
            String message = null;

            if (!ids.isEmpty() && !repository.isAvailable()) {
                message = "서버 연결 후 약속을 불러올 수 있어요.";
            } else {
                for (int i = 0; i < ids.size(); i++) {
                    String id = ids.get(i);
                    try {
                        Snapshot snapshot = repository.snapshot(id, null);
                        if (snapshot != null) loaded.add(snapshot);
                        else failed++;
                    } catch (ApiException e) {
                        // 만료·탈퇴가 확인돼 참가 자격이 지워졌을 때만 기기 알람도 정리한다.
                        if (!repository.isMember(id)) alarms.cancelLocal(id);
                        else failed++;
                        if (ApiException.RATE_LIMITED.equals(e.code)) {
                            failed += ids.size() - i - 1;
                            retryDelay = Math.max(1, e.retryAfterSeconds) * 1000L;
                            break;
                        }
                    }
                }
                if (failed > 0) message = loaded.isEmpty() ? "약속을 불러오지 못했어요. 연결 상태를 확인해 주세요."
                        : failed + "개의 약속을 불러오지 못했어요. 다시 시도해 주세요.";
                if (retryDelay > 0) message = "요청이 많아요. " + retryDelay / 1000 + "초 뒤 다시 시도해 주세요.";
            }

            // 날짜가 없는 약속은 뒤로, 날짜가 같은 경우 이름과 ID로 순서를 고정한다.
            loaded.sort((a, b) -> {
                Long at = a.meeting.meetAtMillis, bt = b.meeting.meetAtMillis;
                int comparison = Long.compare(at == null ? Long.MAX_VALUE : at, bt == null ? Long.MAX_VALUE : bt);
                if (comparison != 0) return comparison;
                comparison = title(a).compareTo(title(b));
                return comparison != 0 ? comparison : a.meeting.id.compareTo(b.meeting.id);
            });
            final String resultMessage = message;
            final long wait = retryDelay;
            main.post(() -> {
                if (cleared) return;
                fetching = false;
                retryAt = android.os.SystemClock.uptimeMillis() + wait;
                state.setValue(new State(loaded, false, resultMessage));
            });
        });
    }

    private static String title(Snapshot snapshot) {
        return snapshot.meeting.title == null ? "" : snapshot.meeting.title;
    }

    @Override protected void onCleared() {
        cleared = true;
        executor.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }
}
