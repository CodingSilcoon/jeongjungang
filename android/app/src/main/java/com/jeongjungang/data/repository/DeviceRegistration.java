package com.jeongjungang.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import com.jeongjungang.BuildConfig;
import com.jeongjungang.data.remote.ApiException;
import com.jeongjungang.data.remote.meeting.AccountabilityApi;
import com.jeongjungang.data.remote.meeting.HttpAccountabilityApi;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 푸시 받을 기기 등록 (docs/API.md 7절 `POST /devices`). 서버는 참가자(약속)마다 기기를 하나 두므로
 * 들어가 있는 약속마다 등록한다. 같은 토큰으로 이미 등록한 약속은 다시 보내지 않는다.
 * 네트워크 호출이라 백그라운드에서 부른다.
 */
public final class DeviceRegistration {

    /** 약속별로 마지막에 등록한 FCM 토큰. */
    interface Store {
        String registeredToken(String meetingId);

        void markRegistered(String meetingId, String fcmToken);

        /** 나간 약속의 기록을 지운다. */
        void retainOnly(Collection<String> meetingIds);
    }

    /** 지금 들어가 있는 약속들의 내 자격. */
    interface Members {
        List<ParticipantTokenStore.Entry> all();
    }

    private static volatile DeviceRegistration instance;

    private final AccountabilityApi api;
    private final Members members;
    private final Store store;
    private final String appVersion;

    DeviceRegistration(AccountabilityApi api, Members members, Store store, String appVersion) {
        this.api = api;
        this.members = members;
        this.store = store;
        this.appVersion = appVersion;
    }

    public static DeviceRegistration getInstance(Context context) {
        if (instance == null) {
            synchronized (DeviceRegistration.class) {
                if (instance == null) {
                    Context app = context.getApplicationContext();
                    AccountabilityApi api = BuildConfig.API_BASE_URL.isEmpty()
                            ? null : new HttpAccountabilityApi(BuildConfig.API_BASE_URL);
                    instance = new DeviceRegistration(api, tokenMembers(new ParticipantTokenStore(app)),
                            new PrefsStore(app), BuildConfig.VERSION_NAME);
                }
            }
        }
        return instance;
    }

    public boolean isAvailable() {
        return api != null;
    }

    /**
     * @return 모두 등록했거나 다시 보내도 소용없으면 true, 네트워크·서버 문제로 남은 게 있으면 false(나중에 다시)
     */
    public synchronized boolean registerAll(String fcmToken) {
        if (api == null || fcmToken == null || fcmToken.isEmpty()) {
            return true;
        }
        boolean allDone = true;
        List<String> ids = new ArrayList<String>();
        for (ParticipantTokenStore.Entry me : members.all()) {
            ids.add(me.meetingId);
            if (fcmToken.equals(store.registeredToken(me.meetingId))) {
                continue;
            }
            try {
                api.registerDevice(me.token, fcmToken, appVersion);
                store.markRegistered(me.meetingId, fcmToken);
            } catch (ApiException e) {
                if (AccountabilityRepository.isRetryable(e)) {
                    allDone = false;
                }
                // 그 밖의 4xx는 약속이 사라졌거나 내가 빠진 것(401). 다시 보내도 같고, 약속 화면이 토큰을 정리한다
            }
        }
        store.retainOnly(ids);
        return allDone;
    }

    private static Members tokenMembers(final ParticipantTokenStore tokens) {
        return new Members() {
            @Override
            public List<ParticipantTokenStore.Entry> all() {
                List<ParticipantTokenStore.Entry> out = new ArrayList<ParticipantTokenStore.Entry>();
                for (String id : tokens.meetingIds()) {
                    ParticipantTokenStore.Entry e = tokens.get(id);
                    if (e != null) {
                        out.add(e);
                    }
                }
                return out;
            }
        };
    }

    private static final class PrefsStore implements Store {

        private final SharedPreferences prefs;

        PrefsStore(Context context) {
            prefs = context.getSharedPreferences("device_registration", Context.MODE_PRIVATE);
        }

        @Override
        public String registeredToken(String meetingId) {
            return prefs.getString(meetingId, null);
        }

        @Override
        public void markRegistered(String meetingId, String fcmToken) {
            prefs.edit().putString(meetingId, fcmToken).apply();
        }

        @Override
        public void retainOnly(Collection<String> meetingIds) {
            SharedPreferences.Editor e = prefs.edit();
            for (String key : prefs.getAll().keySet()) {
                if (!meetingIds.contains(key)) {
                    e.remove(key);
                }
            }
            e.apply();
        }
    }
}
