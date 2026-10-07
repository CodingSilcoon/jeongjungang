package com.jeongjungang.alarm;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.jeongjungang.data.repository.AccountabilityRepository;
import java.util.concurrent.TimeUnit;

/**
 * 알람을 끈 기록을 서버에 보낸다. 네트워크가 있을 때만 돌고, 실패하면 간격을 늘려 다시 시도한다.
 * WorkManager가 앱 종료·재부팅 뒤에도 이어서 실행한다 (CLAUDE.md: dismiss 보고 실패 시 큐에 쌓았다가 재전송).
 */
public class DismissSyncWorker extends Worker {

    private static final String UNIQUE_NAME = "dismiss-sync";

    public DismissSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** 알람을 끈 직후, 그리고 서버 연결 상태가 바뀔 만한 때(약속 화면 열기 등) 부른다. 여러 번 불러도 차례로 돈다. */
    public static void enqueue(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DismissSyncWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        // KEEP이면 이미 도는 작업이 새 기록을 못 보고 끝날 수 있어서, 도는 중이면 뒤에 한 번 더 붙인다
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        AccountabilityRepository repo = AccountabilityRepository.getInstance(getApplicationContext());
        if (!repo.isAvailable()) {
            // 서버 설정 전. 큐는 그대로 두고 끝낸다(서버가 생기면 다음 enqueue 때 보낸다)
            return Result.success();
        }
        return repo.flushDismissals() ? Result.success() : Result.retry();
    }
}
