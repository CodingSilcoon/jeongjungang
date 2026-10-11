package com.jeongjungang.ui.alarm;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.jeongjungang.data.remote.ApiException;
import com.jeongjungang.data.remote.meeting.MeetingModels.Snapshot;
import com.jeongjungang.data.repository.MeetingRepository;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 알람을 먼저 보여 준 뒤 약속 정보만 별도로 조회한다. */
public class AlarmMeetingViewModel extends AndroidViewModel {

    private final MeetingRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<Snapshot> snapshot = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private int generation;

    public AlarmMeetingViewModel(Application app) {
        super(app);
        repository = MeetingRepository.getInstance(app);
    }

    public LiveData<Snapshot> getSnapshot() {
        return snapshot;
    }

    public LiveData<Boolean> isLoading() {
        return loading;
    }

    /** 새 알람이 열리면 이전 조회 결과가 화면을 덮어쓰지 못하게 한다. */
    public void load(String meetingId) {
        final int request = ++generation;
        snapshot.setValue(null);
        loading.setValue(false);
        if (meetingId == null || meetingId.isEmpty() || !repository.isAvailable()) return;
        loading.setValue(true);

        executor.execute(() -> {
            Snapshot result = null;
            try {
                result = repository.snapshot(meetingId, null);
            } catch (ApiException ignored) {
                // 조회 실패는 알람 종료를 막지 않는다. 기본 안내를 유지한다.
            }
            final Snapshot loaded = result;
            main.post(() -> {
                if (request == generation) {
                    snapshot.setValue(loaded);
                    loading.setValue(false);
                }
            });
        });
    }

    @Override
    protected void onCleared() {
        generation++;
        executor.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }
}
