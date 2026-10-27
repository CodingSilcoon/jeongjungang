package com.jeongjungang.push;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.jeongjungang.data.remote.ApiException;
import com.jeongjungang.data.remote.meeting.MeetingModels.Snapshot;
import com.jeongjungang.data.repository.AccountabilityRepository;
import com.jeongjungang.data.repository.AccountabilityRepository.SyncResult;
import com.jeongjungang.data.repository.MeetingRepository;
import java.util.concurrent.TimeUnit;

/**
 * 푸시를 받은 약속의 내 알람을 서버와 맞춘다. 앱이 꺼져 있어도 돈다.
 * 알람 화면 제목·장소를 쓰려고 약속도 함께 가져온다.
 */
public class AlarmSyncWorker extends Worker {

    private static final String TAG = "AlarmSyncWorker";
    private static final String KEY_MEETING_ID = "meetingId";

    public AlarmSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** 같은 약속의 푸시가 연달아 오면 마지막 것만 돌면 된다(서버 상태를 그대로 가져오므로). */
    static void enqueue(Context context, String meetingId) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(AlarmSyncWorker.class)
                .setInputData(new Data.Builder().putString(KEY_MEETING_ID, meetingId).build())
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context)
                .enqueueUniqueWork("alarm-sync-" + meetingId, ExistingWorkPolicy.REPLACE, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        String meetingId = getInputData().getString(KEY_MEETING_ID);
        Context context = getApplicationContext();
        MeetingRepository meetings = MeetingRepository.getInstance(context);
        AccountabilityRepository repo = AccountabilityRepository.getInstance(context);
        if (meetingId == null || !repo.isAvailable()) {
            return Result.success();
        }
        if (!meetings.isMember(meetingId)) {
            repo.cancelLocal(meetingId);
            return Result.success();
        }
        try {
            Snapshot snap = meetings.snapshot(meetingId, null);
            String place = snap.meeting.place == null ? null : snap.meeting.place.name;
            long meetAt = snap.meeting.meetAtMillis == null ? 0 : snap.meeting.meetAtMillis;
            SyncResult r = repo.syncAlarm(meetingId, snap.meeting.title, place, meetAt);
            Log.i(TAG, "푸시로 알람 동기화: " + r);
            return Result.success();
        } catch (ApiException e) {
            if (AccountabilityRepository.isRetryable(e)) {
                return Result.retry();
            }
            // 약속이 사라졌거나 내가 빠졌으면 snapshot이 토큰을 지운다. 그때는 기기 알람도 지운다
            if (!meetings.isMember(meetingId)) {
                repo.cancelLocal(meetingId);
            }
            Log.w(TAG, "알람 동기화 포기: " + e.code);
            return Result.success();
        }
    }
}
