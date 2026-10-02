package com.jeongjungang.ui.meeting;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.jeongjungang.data.remote.ApiException;
import com.jeongjungang.data.remote.meeting.MeetingModels.Membership;
import com.jeongjungang.data.remote.meeting.MeetingModels.Origin;
import com.jeongjungang.data.remote.meeting.MeetingModels.Place;
import com.jeongjungang.data.remote.meeting.MeetingModels.Purpose;
import com.jeongjungang.data.remote.meeting.MeetingModels.Snapshot;
import com.jeongjungang.data.remote.meeting.MeetingUpdate;
import com.jeongjungang.data.remote.meeting.ParticipantUpdate;
import com.jeongjungang.data.repository.MeetingRepository;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 약속 만들기·초대 참가·약속 화면(폴링)·수정·나가기 (docs/API.md 6절).
 *
 * <p>약속 화면은 {@link #open(String)}으로 열고, 화면이 보일 때만 갱신되도록
 * onStart에서 {@link #startPolling()}, onStop에서 {@link #stopPolling()}을 부른다.
 */
public class MeetingViewModel extends AndroidViewModel {

    /** docs/API.md: 3~5초 간격 폴링. */
    public static final long POLL_INTERVAL_MS = 4000;

    private static final String TAG = "MeetingViewModel";

    private final MeetingRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<MeetingState> state = new MutableLiveData<MeetingState>(MeetingState.idle());
    private final MutableLiveData<ActionResult> action = new MutableLiveData<ActionResult>();
    /** 다른 약속을 열면 늘린다. 이전 약속의 늦은 응답을 버린다. */
    private final AtomicInteger openGeneration = new AtomicInteger();

    private String meetingId;
    private Snapshot last;
    private boolean polling;
    private boolean fetching;
    /** 429를 받으면 이 시각(uptime ms)까지 폴링을 쉰다. */
    private long pausedUntil;

    private final Runnable pollTick = new Runnable() {
        @Override
        public void run() {
            refresh();
            if (polling) {
                main.postDelayed(this, POLL_INTERVAL_MS);
            }
        }
    };

    public MeetingViewModel(@NonNull Application application) {
        super(application);
        repository = MeetingRepository.getInstance(application);
    }

    /** 서버가 설정되지 않았으면 false. 약속 기능 버튼을 숨기거나 안내한다. */
    public boolean isAvailable() {
        return repository.isAvailable();
    }

    public LiveData<MeetingState> getState() {
        return state;
    }

    public LiveData<ActionResult> getAction() {
        return action;
    }

    /** 결과를 화면에 반영했으면 부른다. */
    public void consumeAction() {
        action.setValue(null);
    }

    // ---- 약속 열기와 폴링 ----

    /** 약속 화면을 연다. 바로 한 번 불러오고, 폴링 중이면 이어서 갱신한다. */
    public void open(String id) {
        if (id == null) {
            return;
        }
        if (id.equals(meetingId) && last != null) {
            refresh();
            return;
        }
        openGeneration.incrementAndGet();
        meetingId = id;
        last = null;
        state.setValue(MeetingState.loading(id));
        refresh();
    }

    public void close() {
        openGeneration.incrementAndGet();
        meetingId = null;
        last = null;
        state.setValue(MeetingState.idle());
    }

    public void startPolling() {
        if (polling) {
            return;
        }
        polling = true;
        main.post(pollTick);
    }

    public void stopPolling() {
        polling = false;
        main.removeCallbacks(pollTick);
    }

    /** 지금 바로 갱신한다(당겨서 새로고침 등). */
    public void refresh() {
        final String id = meetingId;
        if (id == null || fetching || android.os.SystemClock.uptimeMillis() < pausedUntil) {
            return;
        }
        fetching = true;
        final int gen = openGeneration.get();
        final String etag = last == null ? null : last.etag;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                Snapshot fresh = null;
                ApiException failure = null;
                try {
                    fresh = repository.snapshot(id, etag);
                } catch (ApiException e) {
                    failure = e;
                }
                final Snapshot f = fresh;
                final ApiException err = failure;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        fetching = false;
                        if (gen != openGeneration.get()) {
                            return;
                        }
                        applyFetch(id, f, err);
                    }
                });
            }
        });
    }

    private void applyFetch(String id, Snapshot fresh, ApiException err) {
        if (err == null) {
            if (fresh != null) {
                last = fresh;
            }
            if (last != null) {
                // fresh == null 은 304: 바뀐 게 없으니 이전 상태 그대로, 안내만 지운다
                MeetingState cur = state.getValue();
                if (fresh != null || cur == null || cur.message != null || cur.status != MeetingState.Status.READY) {
                    state.setValue(MeetingState.ready(id, last, null));
                }
            }
            return;
        }
        Log.w(TAG, "약속 갱신 실패: " + err.code, err);
        if (MeetingRepository.NOT_MEMBER.equals(err.code) || isGoneCode(err)) {
            stopPolling();
            last = null;
            state.setValue(MeetingState.gone(id, goneMessage(err)));
            return;
        }
        if (ApiException.RATE_LIMITED.equals(err.code)) {
            pausedUntil = android.os.SystemClock.uptimeMillis() + Math.max(1, err.retryAfterSeconds) * 1000L;
        }
        if (last != null) {
            state.setValue(MeetingState.ready(id, last, err.isNetwork() ? "연결이 불안정해요. 다시 연결하는 중…" : err.getMessage()));
        } else {
            state.setValue(MeetingState.error(id, err.getMessage()));
        }
    }

    private static boolean isGoneCode(ApiException e) {
        return "MEETING_NOT_FOUND".equals(e.code) || "MEETING_EXPIRED".equals(e.code)
                || "UNAUTHORIZED".equals(e.code) || e.httpStatus == 401 || e.httpStatus == 410;
    }

    private static String goneMessage(ApiException e) {
        if ("MEETING_EXPIRED".equals(e.code)) {
            return "기간이 지나 정리된 약속이에요.";
        }
        if (MeetingRepository.NOT_MEMBER.equals(e.code)) {
            return e.getMessage();
        }
        return "약속이 취소됐거나 더 이상 참가하고 있지 않아요.";
    }

    // ---- 만들기와 참가 ----

    /** 약속을 만든다. 성공하면 그 약속을 연다. action.membership.meeting.inviteUrl을 공유하면 된다. */
    public void create(final String hostNickname, final String title, final Purpose purpose,
                       final Long meetAtMillis, final Origin origin) {
        run(ActionResult.Kind.CREATE, new Task() {
            @Override
            public ActionResult call() throws ApiException {
                Membership m = repository.create(hostNickname, title, purpose, meetAtMillis, origin);
                return ActionResult.joined(ActionResult.Kind.CREATE, m);
            }
        });
    }

    /** 초대 링크나 코드로 약속 미리보기. 링크 그대로 넘겨도 된다. */
    public void preview(final String inviteLinkOrCode) {
        run(ActionResult.Kind.PREVIEW, new Task() {
            @Override
            public ActionResult call() throws ApiException {
                return ActionResult.previewed(repository.preview(inviteLinkOrCode));
            }
        });
    }

    /** 초대 코드로 참가한다. 출발지는 null이면 나중에 {@link #updateMe}로 넣는다. 성공하면 그 약속을 연다. */
    public void join(final String inviteLinkOrCode, final String nickname, final Origin origin) {
        run(ActionResult.Kind.JOIN, new Task() {
            @Override
            public ActionResult call() throws ApiException {
                return ActionResult.joined(ActionResult.Kind.JOIN,
                        repository.join(inviteLinkOrCode, nickname, origin));
            }
        });
    }

    // ---- 약속 화면 동작 ----

    /** 방장: 약속 정보 수정. */
    public void updateMeeting(final MeetingUpdate update) {
        final String id = requireOpen(ActionResult.Kind.UPDATE_MEETING);
        if (id == null) {
            return;
        }
        run(ActionResult.Kind.UPDATE_MEETING, new Task() {
            @Override
            public ActionResult call() throws ApiException {
                repository.update(id, update);
                return ActionResult.done(ActionResult.Kind.UPDATE_MEETING);
            }
        });
    }

    /** 방장: 추천 후보 중 하나로 장소·시간 확정. */
    public void confirm(Place place, long meetAtMillis) {
        updateMeeting(new MeetingUpdate().place(place).meetAt(meetAtMillis).confirm());
    }

    /** 내 이름·출발지 등 수정. */
    public void updateMe(final ParticipantUpdate update) {
        final String id = requireOpen(ActionResult.Kind.UPDATE_ME);
        if (id == null) {
            return;
        }
        run(ActionResult.Kind.UPDATE_ME, new Task() {
            @Override
            public ActionResult call() throws ApiException {
                repository.updateMe(id, update);
                return ActionResult.done(ActionResult.Kind.UPDATE_ME);
            }
        });
    }

    /** 약속에서 나가기. 방장이면 가장 먼저 들어온 사람에게 넘어가고, 혼자면 약속이 취소된다. */
    public void leave() {
        final String id = requireOpen(ActionResult.Kind.LEAVE);
        if (id == null) {
            return;
        }
        run(ActionResult.Kind.LEAVE, new Task() {
            @Override
            public ActionResult call() throws ApiException {
                repository.leave(id);
                return ActionResult.done(ActionResult.Kind.LEAVE);
            }
        });
    }

    /** 방장: 약속 취소. */
    public void cancelMeeting() {
        final String id = requireOpen(ActionResult.Kind.CANCEL);
        if (id == null) {
            return;
        }
        run(ActionResult.Kind.CANCEL, new Task() {
            @Override
            public ActionResult call() throws ApiException {
                repository.cancel(id);
                return ActionResult.done(ActionResult.Kind.CANCEL);
            }
        });
    }

    /** 방장: 참가자 내보내기. */
    public void kick(final String participantId) {
        final String id = requireOpen(ActionResult.Kind.KICK);
        if (id == null) {
            return;
        }
        run(ActionResult.Kind.KICK, new Task() {
            @Override
            public ActionResult call() throws ApiException {
                repository.kick(id, participantId);
                return ActionResult.done(ActionResult.Kind.KICK);
            }
        });
    }

    // ---- 내부 ----

    private interface Task {
        ActionResult call() throws ApiException;
    }

    private String requireOpen(ActionResult.Kind kind) {
        if (meetingId == null) {
            action.setValue(ActionResult.failed(kind, "열린 약속이 없어요."));
        }
        return meetingId;
    }

    private void run(final ActionResult.Kind kind, final Task task) {
        action.setValue(ActionResult.running(kind));
        executor.execute(new Runnable() {
            @Override
            public void run() {
                ActionResult result;
                try {
                    result = task.call();
                } catch (ApiException e) {
                    Log.w(TAG, kind + " 실패: " + e.code, e);
                    result = ActionResult.failed(kind, e.getMessage());
                } catch (RuntimeException e) {
                    Log.e(TAG, kind + " 오류", e);
                    result = ActionResult.failed(kind, "처리하지 못했어요. 잠시 후 다시 시도해 주세요.");
                }
                final ActionResult r = result;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        afterAction(r);
                    }
                });
            }
        });
    }

    private void afterAction(ActionResult r) {
        action.setValue(r);
        if (r.status != ActionResult.Status.DONE) {
            return;
        }
        switch (r.kind) {
            case CREATE:
            case JOIN:
                open(r.membership.meetingId);
                break;
            case LEAVE:
            case CANCEL:
                stopPolling();
                close();
                break;
            case UPDATE_MEETING:
            case UPDATE_ME:
            case KICK:
                refresh();
                break;
            default:
                break;
        }
    }

    @Override
    protected void onCleared() {
        stopPolling();
        executor.shutdownNow();
    }
}
