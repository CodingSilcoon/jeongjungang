package com.jeongjungang.ui.meeting;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.jeongjungang.data.remote.ApiException;
import com.jeongjungang.data.remote.meeting.HttpMeetingApi;
import com.jeongjungang.data.remote.meeting.MeetingModels.NearbyPlace;
import com.jeongjungang.data.remote.meeting.MeetingModels.PlacePage;
import com.jeongjungang.data.remote.meeting.MeetingModels.Purpose;
import com.jeongjungang.data.repository.MeetingRepository;
import com.jeongjungang.domain.model.LatLng;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 약속 목적 필터: 확정한 역 주변의 식당·카페·술집 (docs/API.md `GET /places`).
 * {@link #search}로 첫 페이지, 목록 끝에 닿으면 {@link #loadMore()}.
 */
public class PlacesViewModel extends AndroidViewModel {

    /** 장소 목록 상태. */
    public static final class State {
        public enum Status { IDLE, LOADING, SUCCESS, EMPTY, ERROR }

        public final Status status;
        /** 지금까지 불러온 전체 목록(페이지 누적). */
        public final List<NearbyPlace> items;
        public final boolean hasMore;
        /** 다음 페이지를 불러오는 중이면 true (목록 아래 로딩 표시). */
        public final boolean loadingMore;
        public final String errorMessage;

        State(Status status, List<NearbyPlace> items, boolean hasMore, boolean loadingMore, String errorMessage) {
            this.status = status;
            this.items = Collections.unmodifiableList(new ArrayList<NearbyPlace>(items));
            this.hasMore = hasMore;
            this.loadingMore = loadingMore;
            this.errorMessage = errorMessage;
        }
    }

    private static final String TAG = "PlacesViewModel";

    private final MeetingRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<State> state = new MutableLiveData<State>(
            new State(State.Status.IDLE, Collections.<NearbyPlace>emptyList(), false, false, null));
    private final AtomicInteger generation = new AtomicInteger();

    private LatLng center;
    private String category;
    private int radius;
    /** 마지막으로 받은 페이지. 백그라운드에서 쓰고 메인에서 읽는다. */
    private volatile int page;
    private final List<NearbyPlace> loaded = new ArrayList<NearbyPlace>();

    public PlacesViewModel(@NonNull Application application) {
        super(application);
        repository = MeetingRepository.getInstance(application);
    }

    public LiveData<State> getState() {
        return state;
    }

    /** 약속 목적으로 검색. ETC는 카테고리가 없어서 {@link #search(LatLng, String, int)}로 직접 고르게 한다. */
    public void search(LatLng center, Purpose purpose) {
        search(center, purpose.placeCategory, HttpMeetingApi.DEFAULT_RADIUS);
    }

    /** @param category FOOD, CAFE, BAR */
    public void search(LatLng center, String category, int radiusMeters) {
        this.center = center;
        this.category = category;
        this.radius = radiusMeters;
        this.page = 0;
        generation.incrementAndGet();
        synchronized (loaded) {
            loaded.clear();
        }
        if (category == null) {
            state.setValue(new State(State.Status.ERROR, Collections.<NearbyPlace>emptyList(), false, false,
                    "식당·카페·술집 중에서 골라 주세요."));
            return;
        }
        state.setValue(new State(State.Status.LOADING, Collections.<NearbyPlace>emptyList(), false, false, null));
        fetch(1);
    }

    /** 다음 페이지. 더 없거나 불러오는 중이면 아무것도 안 한다. */
    public void loadMore() {
        State cur = state.getValue();
        if (cur == null || cur.status != State.Status.SUCCESS || !cur.hasMore || cur.loadingMore) {
            return;
        }
        state.setValue(new State(State.Status.SUCCESS, cur.items, true, true, null));
        fetch(page + 1);
    }

    private void fetch(final int nextPage) {
        final int gen = generation.get();
        final LatLng c = center;
        final String cat = category;
        final int r = radius;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    PlacePage result = repository.places(c, cat, r, nextPage);
                    if (gen != generation.get()) {
                        return;
                    }
                    synchronized (loaded) {
                        loaded.addAll(result.items);
                        page = result.page;
                        state.postValue(new State(loaded.isEmpty() ? State.Status.EMPTY : State.Status.SUCCESS,
                                loaded, result.hasNext, false, null));
                    }
                } catch (ApiException e) {
                    Log.w(TAG, "장소 검색 실패: " + e.code, e);
                    if (gen != generation.get()) {
                        return;
                    }
                    synchronized (loaded) {
                        // 다음 페이지 실패면 지금까지 목록은 유지하고 더 불러오기만 다시 할 수 있게 한다
                        state.postValue(loaded.isEmpty()
                                ? new State(State.Status.ERROR, loaded, false, false, e.getMessage())
                                : new State(State.Status.SUCCESS, loaded, true, false, e.getMessage()));
                    }
                }
            }
        });
    }

    @Override
    protected void onCleared() {
        executor.shutdownNow();
    }
}
