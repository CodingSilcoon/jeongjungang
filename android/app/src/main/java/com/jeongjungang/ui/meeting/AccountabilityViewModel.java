package com.jeongjungang.ui.meeting;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.jeongjungang.alarm.DismissSyncWorker;
import com.jeongjungang.data.remote.ApiException;
import com.jeongjungang.data.remote.meeting.AccountabilityApi.AlarmStatus;
import com.jeongjungang.data.remote.meeting.HttpAccountabilityApi;
import com.jeongjungang.data.remote.meeting.MeetingModels.Member;
import com.jeongjungang.data.remote.meeting.MeetingModels.Snapshot;
import com.jeongjungang.data.remote.meeting.ParticipantUpdate;
import com.jeongjungang.data.repository.AccountabilityRepository;
import com.jeongjungang.data.repository.AccountabilityRepository.SyncResult;
import com.jeongjungang.data.repository.MeetingRepository;
import com.jeongjungang.data.repository.TransitData;
import com.jeongjungang.data.repository.TransitRepository;
import com.jeongjungang.domain.alarm.AlarmTimeCalculator;
import com.jeongjungang.domain.recommend.Participant;
import com.jeongjungang.domain.recommend.PersonTrip;
import com.jeongjungang.domain.transit.StationNames;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 책임 알람 화면 (docs/API.md 7절). 약속 화면이 {@link MeetingViewModel}의 새 snapshot을 받을 때마다
 * {@link #onSnapshot(Snapshot)}을 불러 주면, 이동시간 보고와 기기 알람 동기화는 알아서 한다.
 */
public class AccountabilityViewModel extends AndroidViewModel {

    /** 화면이 그릴 상태. */
    public static final class State {
        /** 확정 장소까지 내 이동시간(보고한 값). 아직 없으면 null. */
        public final Double travelMinutes;
        public final int travelTransfers;
        /** 마지막 기기 알람 동기화 결과. 아직 안 했으면 null. */
        public final SyncResult sync;
        /** 진행 중인 버튼 동작이 있으면 true. */
        public final boolean busy;
        /** 잠깐 보여 줄 안내(실패 이유 등). 없으면 null. 보여 줬으면 {@link #consumeMessage()}. */
        public final String message;

        State(Double travelMinutes, int travelTransfers, SyncResult sync, boolean busy, String message) {
            this.travelMinutes = travelMinutes;
            this.travelTransfers = travelTransfers;
            this.sync = sync;
            this.busy = busy;
            this.message = message;
        }

        State withMessage(String m) {
            return new State(travelMinutes, travelTransfers, sync, busy, m);
        }

        State withBusy(boolean b) {
            return new State(travelMinutes, travelTransfers, sync, b, message);
        }
    }

    private static final String TAG = "AccountabilityVM";

    private final AccountabilityRepository repo;
    private final MeetingRepository meetings;
    private final TransitRepository transit;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<State> state = new MutableLiveData<State>(new State(null, 0, null, false, null));

    // 같은 내용을 반복해서 보내지 않도록 마지막으로 처리한 값을 기억한다 (메인 스레드에서만 접근)
    private String reportedKey;
    private long syncedVersion = -1;

    public AccountabilityViewModel(@NonNull Application application) {
        super(application);
        repo = AccountabilityRepository.getInstance(application);
        meetings = MeetingRepository.getInstance(application);
        transit = TransitRepository.getInstance(application);
        // 앞서 못 보낸 끔 기록이 있으면 이참에 보낸다
        DismissSyncWorker.enqueue(application);
    }

    public boolean isAvailable() {
        return repo.isAvailable();
    }

    public LiveData<State> getState() {
        return state;
    }

    public void consumeMessage() {
        State s = state.getValue();
        if (s != null && s.message != null) {
            state.setValue(s.withMessage(null));
        }
    }

    /**
     * 약속 snapshot이 바뀔 때마다 부른다.
     * <ul>
     *   <li>장소가 확정됐고 내 출발지가 있으면, 그 장소까지 이동시간을 계산해 서버에 보고한다(장소·출발지가 바뀌면 다시)</li>
     *   <li>책임 알람 상태가 바뀌면 서버의 내 알람을 기기 알람과 맞춘다</li>
     * </ul>
     */
    public void onSnapshot(final Snapshot snap) {
        if (snap == null || !repo.isAvailable()) {
            return;
        }
        final String meetingId = snap.meeting.id;
        final Member me = snap.me();
        if (snap.meeting.place != null && me != null && me.origin != null) {
            final String key = meetingId + "|" + snap.meeting.place.name + "|"
                    + me.origin.location.lat + "," + me.origin.location.lng;
            if (!key.equals(reportedKey)) {
                reportedKey = key;
                final String placeName = snap.meeting.place.name;
                final Participant self = new Participant(me.nickname, me.origin.location);
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        reportTravel(meetingId, placeName, self);
                    }
                });
            }
        }
        if (snap.version != syncedVersion) {
            syncedVersion = snap.version;
            final String title = snap.meeting.title;
            final String place = snap.meeting.place == null ? null : snap.meeting.place.name;
            final long meetAt = snap.meeting.meetAtMillis == null ? 0 : snap.meeting.meetAtMillis;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        final SyncResult r = repo.syncAlarm(meetingId, title, place, meetAt);
                        post(new Mutator() {
                            @Override
                            public State apply(State s) {
                                return new State(s.travelMinutes, s.travelTransfers, r, s.busy,
                                        r == SyncResult.NEEDS_EXACT_ALARM_PERMISSION
                                                ? "알람을 예약하려면 '알람 및 리마인더' 권한이 필요해요." : s.message);
                            }
                        });
                    } catch (ApiException e) {
                        Log.w(TAG, "알람 동기화 실패: " + e.code, e);
                        // 다음 snapshot에서 다시 시도하도록 버전을 되돌린다
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                syncedVersion = -1;
                            }
                        });
                    }
                }
            });
        }
    }

    private void reportTravel(String meetingId, String placeName, Participant self) {
        try {
            TransitData data = transit.get();
            PersonTrip trip = data.recommender.tripTo(self, StationNames.normalize(placeName));
            if (trip == null) {
                postMessage(placeName + "까지 가는 길을 찾지 못했어요. 출발지를 확인해 주세요.");
                return;
            }
            repo.reportTravel(meetingId, placeName, trip.minutes, trip.transfers);
            final PersonTrip t = trip;
            post(new Mutator() {
                @Override
                public State apply(State s) {
                    return new State(t.minutes, t.transfers, s.sync, s.busy, s.message);
                }
            });
        } catch (ApiException e) {
            Log.w(TAG, "이동시간 보고 실패: " + e.code, e);
            main.post(new Runnable() {
                @Override
                public void run() {
                    reportedKey = null; // 다음 snapshot에서 다시 시도
                }
            });
            if (!e.isNetwork()) {
                postMessage(e.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, "이동시간 계산 실패", e);
            postMessage("이동시간을 계산하지 못했어요.");
        }
    }

    // ---- 버튼 동작 ----

    /** 내 준비시간(0~240분)과 책임 알람 동의를 함께 저장한다. */
    public void savePreferences(String meetingId, int prepMinutes, boolean optedIn) {
        final ParticipantUpdate u = new ParticipantUpdate().prepMinutes(prepMinutes).optedIn(optedIn);
        runAction(meetingId, new Action() {
            @Override
            public String run(String id) throws ApiException {
                meetings.updateMe(id, u);
                return null;
            }
        });
    }

    /** 방장: 책임 알람 켜기. 기본 유예시간 60초, 여유시간 5분. */
    public void enable(String meetingId) {
        enable(meetingId, HttpAccountabilityApi.DEFAULT_GRACE_SEC, AlarmTimeCalculator.DEFAULT_MARGIN_MINUTES);
    }

    public void enable(String meetingId, final int gracePeriodSec, final int marginMinutes) {
        runAction(meetingId, new Action() {
            @Override
            public String run(String id) throws ApiException {
                repo.enable(id, gracePeriodSec, marginMinutes);
                return "책임 알람을 켰어요.";
            }
        });
    }

    /** 방장: 책임 알람 끄기. */
    public void disable(String meetingId) {
        runAction(meetingId, new Action() {
            @Override
            public String run(String id) throws ApiException {
                repo.disable(id);
                return "책임 알람을 껐어요.";
            }
        });
    }

    /** "이미 일어났어요" 버튼. 알람이 울리기 전에도 누를 수 있다. */
    public void reportAwake(String meetingId) {
        runAction(meetingId, new Action() {
            @Override
            public String run(String id) throws ApiException {
                AlarmStatus s = repo.reportAwake(id);
                return s == AlarmStatus.ESCALATED
                        ? "이미 친구들에게 알림이 갔어요. 일어났다고 연락해 주세요."
                        : "일어났다고 알렸어요. 오늘 알람은 울리지 않아요.";
            }
        });
    }

    /** 약속에서 나가거나 약속이 사라졌을 때 이 기기의 알람을 지운다. */
    public void forgetMeeting(final String meetingId) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                repo.cancelLocal(meetingId);
            }
        });
    }

    // ---- 내부 ----

    private interface Action {
        /** @return 성공 시 보여 줄 문장(없으면 null) */
        String run(String meetingId) throws ApiException;
    }

    private interface Mutator {
        State apply(State s);
    }

    private void runAction(final String meetingId, final Action action) {
        State s = state.getValue();
        state.setValue((s == null ? new State(null, 0, null, false, null) : s).withBusy(true));
        executor.execute(new Runnable() {
            @Override
            public void run() {
                String message;
                try {
                    message = action.run(meetingId);
                } catch (ApiException e) {
                    Log.w(TAG, "동작 실패: " + e.code, e);
                    message = messageFor(e);
                } catch (RuntimeException e) {
                    Log.e(TAG, "동작 오류", e);
                    message = "처리하지 못했어요. 잠시 후 다시 시도해 주세요.";
                }
                final String m = message;
                post(new Mutator() {
                    @Override
                    public State apply(State cur) {
                        return new State(cur.travelMinutes, cur.travelTransfers, cur.sync, false,
                                m != null ? m : cur.message);
                    }
                });
                // 상태가 바뀌었을 수 있으니 다음 snapshot에서 알람을 다시 맞춘다
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        syncedVersion = -1;
                    }
                });
            }
        });
    }

    /** 서버가 준 문장이 있으면 그대로, 409 두 가지는 앱이 이해하기 쉬운 문장으로. */
    static String messageFor(ApiException e) {
        if ("NOT_ALL_OPTED_IN".equals(e.code)) {
            return "모두가 책임 알람에 동의해야 켤 수 있어요.";
        }
        if ("ALARM_NOT_READY".equals(e.code)) {
            return "장소와 시간을 먼저 확정해 주세요.";
        }
        return e.getMessage();
    }

    private void postMessage(final String m) {
        post(new Mutator() {
            @Override
            public State apply(State s) {
                return s.withMessage(m);
            }
        });
    }

    private void post(final Mutator mutator) {
        main.post(new Runnable() {
            @Override
            public void run() {
                State s = state.getValue();
                state.setValue(mutator.apply(s == null ? new State(null, 0, null, false, null) : s));
            }
        });
    }

    @Override
    protected void onCleared() {
        executor.shutdownNow();
    }
}
