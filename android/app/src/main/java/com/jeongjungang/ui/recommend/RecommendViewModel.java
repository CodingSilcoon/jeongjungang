package com.jeongjungang.ui.recommend;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.jeongjungang.data.repository.TransitData;
import com.jeongjungang.data.repository.TransitRepository;
import com.jeongjungang.domain.model.LatLng;
import com.jeongjungang.domain.recommend.Criterion;
import com.jeongjungang.domain.recommend.Participant;
import com.jeongjungang.domain.recommend.Recommendation;
import com.jeongjungang.domain.recommend.ServiceArea;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 출발지 목록과 기준을 받아 백그라운드에서 후보 3곳을 계산하고 {@link RecommendState}로 내보낸다.
 * 화면은 {@link #getState()}를 관찰하고 {@link #recommend(List, Criterion)}만 부르면 된다.
 */
public class RecommendViewModel extends AndroidViewModel {

    /** docs/API.md의 참가자 상한과 맞춘다. */
    public static final int MAX_PARTICIPANTS = 10;

    private static final String TAG = "RecommendViewModel";

    private final TransitRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<RecommendState> state = new MutableLiveData<RecommendState>(RecommendState.idle());
    /** 새 요청이 들어오면 늘린다. 이전 요청의 결과가 늦게 도착해도 덮어쓰지 않게 한다. */
    private final AtomicInteger generation = new AtomicInteger();

    public RecommendViewModel(@NonNull Application application) {
        super(application);
        repository = TransitRepository.getInstance(application);
        // 첫 추천이 빨라지도록 그래프를 미리 만들어 둔다.
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    repository.get();
                } catch (Exception e) {
                    Log.w(TAG, "지하철 데이터 미리 읽기 실패", e);
                }
            }
        });
    }

    public LiveData<RecommendState> getState() {
        return state;
    }

    /** 메인 스레드에서 부른다. 계산 중에 다시 부르면 마지막 요청의 결과만 반영된다. */
    public void recommend(List<Participant> participants, final Criterion criterion) {
        final List<Participant> input = participants == null
                ? Collections.<Participant>emptyList()
                : Collections.unmodifiableList(new ArrayList<Participant>(participants));
        final int requestId = generation.incrementAndGet();

        String invalid = validate(input, criterion);
        if (invalid != null) {
            state.setValue(RecommendState.error(input, criterion, invalid));
            return;
        }
        state.setValue(RecommendState.loading(input, criterion));
        executor.execute(new Runnable() {
            @Override
            public void run() {
                RecommendState next;
                try {
                    TransitData data = repository.get();
                    List<Participant> outside = ServiceArea.outside(data.index, input);
                    if (!outside.isEmpty()) {
                        next = RecommendState.outOfArea(input, criterion, outside, ServiceArea.messageFor(outside));
                    } else {
                        List<Recommendation> results = data.recommender.recommend(input, criterion);
                        Map<String, LatLng> coords = new LinkedHashMap<String, LatLng>();
                        for (Recommendation r : results) {
                            coords.put(r.station, data.index.coordinateOf(r.station));
                        }
                        next = RecommendState.result(input, criterion, results, coords);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "추천 계산 실패", e);
                    next = RecommendState.error(input, criterion, "추천을 계산하지 못했어요. 잠시 후 다시 시도해 주세요.");
                }
                if (requestId == generation.get()) {
                    state.postValue(next);
                }
            }
        });
    }

    /** 결과를 지우고 처음 상태로 돌린다. 진행 중인 계산 결과는 버린다. */
    public void reset() {
        generation.incrementAndGet();
        state.setValue(RecommendState.idle());
    }

    /** @return 문제가 없으면 null, 있으면 사용자에게 보여 줄 문장 */
    static String validate(List<Participant> participants, Criterion criterion) {
        if (criterion == null) {
            return "추천 기준을 골라 주세요.";
        }
        if (participants.size() < 2) {
            return "출발지를 2곳 이상 입력해 주세요.";
        }
        if (participants.size() > MAX_PARTICIPANTS) {
            return "출발지는 최대 " + MAX_PARTICIPANTS + "곳까지 입력할 수 있어요.";
        }
        return null;
    }

    @Override
    protected void onCleared() {
        executor.shutdownNow();
    }
}
