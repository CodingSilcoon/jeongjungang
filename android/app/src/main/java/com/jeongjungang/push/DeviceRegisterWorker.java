package com.jeongjungang.push;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.messaging.FirebaseMessaging;
import com.jeongjungang.data.repository.DeviceRegistration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 들어가 있는 약속마다 이 기기의 FCM 토큰을 서버에 등록한다(`POST /devices`). 이미 같은 토큰으로 등록한 약속은 건너뛴다.
 * 앱 시작, 약속 화면 열기, 토큰 변경 때 부른다. 여러 번 불러도 된다.
 */
public class DeviceRegisterWorker extends Worker {

    private static final String TAG = "DeviceRegisterWorker";
    private static final String UNIQUE_NAME = "device-register";
    private static final long TOKEN_TIMEOUT_SEC = 30;

    public DeviceRegisterWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void enqueue(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DeviceRegisterWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        // 도는 중에 새 약속에 들어가거나 토큰이 바뀌면 끝난 뒤 한 번 더 돈다
        WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        DeviceRegistration registration = DeviceRegistration.getInstance(getApplicationContext());
        if (!registration.isAvailable()) {
            return Result.success();
        }
        String fcmToken;
        try {
            fcmToken = Tasks.await(FirebaseMessaging.getInstance().getToken(), TOKEN_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            // Google Play 서비스가 없거나 아직 연결 전. 나중에 다시 해 본다
            Log.w(TAG, "FCM 토큰을 받지 못함", e);
            return Result.retry();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.retry();
        }
        return registration.registerAll(fcmToken) ? Result.success() : Result.retry();
    }
}
