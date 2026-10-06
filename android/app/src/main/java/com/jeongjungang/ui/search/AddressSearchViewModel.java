package com.jeongjungang.ui.search;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.jeongjungang.data.remote.ApiException;
import com.jeongjungang.data.remote.GeoPlace;
import com.jeongjungang.data.remote.GeocodeApi;
import com.jeongjungang.data.remote.ReverseAddress;
import com.jeongjungang.data.repository.GeocodeRepository;
import com.jeongjungang.data.repository.TransitData;
import com.jeongjungang.data.repository.TransitRepository;
import com.jeongjungang.domain.recommend.ServiceArea;
import com.jeongjungang.domain.model.LatLng;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 출발지 입력의 주소 검색과 지도 핀 주소 조회.
 *
 * <p>검색은 두 방식을 모두 지원한다. 화면이 하나만 써도 되고 둘 다 써도 된다.
 * <ul>
 *   <li>자동 검색: 글자가 바뀔 때마다 {@link #onQueryChanged(String)}. 입력이 {@value #DEBOUNCE_MS}ms 멈추면 검색한다</li>
 *   <li>버튼 검색: {@link #search(String)}. 바로 검색한다</li>
 * </ul>
 */
public class AddressSearchViewModel extends AndroidViewModel {

    /** 자동 검색 대기 시간. 서버 제한(IP당 30회/분)을 넘지 않게 한다. */
    public static final long DEBOUNCE_MS = 300;
    public static final int DEFAULT_RESULT_SIZE = 5;

    private static final String TAG = "AddressSearchViewModel";

    private final GeocodeApi api;
    private final TransitRepository transit;
    private final boolean usesServer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<SearchState> searchState = new MutableLiveData<SearchState>(SearchState.idle(""));
    private final MutableLiveData<PinState> pinState = new MutableLiveData<PinState>();
    private final AtomicInteger searchGeneration = new AtomicInteger();
    private final AtomicInteger pinGeneration = new AtomicInteger();

    private int resultSize = DEFAULT_RESULT_SIZE;
    private Runnable pendingSearch;
    /** 서버가 429를 주면 이 시각(elapsedRealtime)까지 자동 검색을 쉰다. */
    private long rateLimitedUntil;

    public AddressSearchViewModel(@NonNull Application application) {
        super(application);
        api = GeocodeRepository.create(application);
        transit = TransitRepository.getInstance(application);
        usesServer = GeocodeRepository.usesServer();
    }

    public LiveData<SearchState> getSearchState() {
        return searchState;
    }

    public LiveData<PinState> getPinState() {
        return pinState;
    }

    /**
     * 서버 없이 번들 역 이름만 검색하는 중이면 false.
     * 화면에 "지금은 지하철역 이름으로만 검색돼요" 같은 안내를 띄울 때 쓴다.
     */
    public boolean usesServer() {
        return usesServer;
    }

    /** 결과 개수(1~10). 다음 검색부터 적용된다. */
    public void setResultSize(int size) {
        resultSize = Math.max(1, Math.min(GeocodeApi.MAX_SIZE, size));
    }

    /** 자동 검색용. EditText의 글자가 바뀔 때마다 부른다. 너무 짧으면 목록만 비운다. */
    public void onQueryChanged(String text) {
        final String query = normalize(text);
        cancelPending();
        if (query.length() < GeocodeApi.MIN_QUERY_LENGTH) {
            searchGeneration.incrementAndGet();
            searchState.setValue(SearchState.idle(query));
            return;
        }
        SearchState current = searchState.getValue();
        if (current != null && query.equals(current.query) && current.status != SearchState.Status.ERROR) {
            return; // 같은 검색어(공백만 바뀜 등)는 다시 부르지 않는다
        }
        if (SystemClock.elapsedRealtime() < rateLimitedUntil) {
            return; // 제한 안내가 떠 있는 동안은 자동 검색을 보내지 않는다
        }
        pendingSearch = new Runnable() {
            @Override
            public void run() {
                pendingSearch = null;
                startSearch(query);
            }
        };
        main.postDelayed(pendingSearch, DEBOUNCE_MS);
    }

    /** 버튼·키보드 검색용. 기다리지 않고 바로 검색한다. 너무 짧으면 안내 문구를 보여 준다. */
    public void search(String text) {
        String query = normalize(text);
        cancelPending();
        String invalid = validate(query);
        if (invalid != null) {
            searchGeneration.incrementAndGet();
            searchState.setValue(SearchState.error(query, invalid));
            return;
        }
        startSearch(query);
    }

    /** 지도에서 핀을 찍거나 옮겼을 때. 마지막으로 찍은 핀의 결과만 반영된다. */
    public void resolvePin(final LatLng location) {
        if (location == null) {
            return;
        }
        final int id = pinGeneration.incrementAndGet();
        pinState.setValue(PinState.loading(location));
        executor.execute(new Runnable() {
            @Override
            public void run() {
                PinState next;
                try {
                    ReverseAddress result = api.reverse(location);
                    next = PinState.resolved(location, result.displayText(), isOutOfArea(location));
                } catch (ApiException e) {
                    Log.w(TAG, "핀 주소 조회 실패: " + e.code, e);
                    next = PinState.failed(location, e.getMessage(), isOutOfArea(location));
                } catch (RuntimeException e) {
                    Log.e(TAG, "핀 주소 조회 오류", e);
                    next = PinState.failed(location, null, isOutOfArea(location));
                }
                if (id == pinGeneration.get()) {
                    pinState.postValue(next);
                }
            }
        });
    }

    /** 검색창을 비우거나 화면을 닫을 때. 진행 중인 검색 결과는 버린다. */
    public void clear() {
        cancelPending();
        searchGeneration.incrementAndGet();
        pinGeneration.incrementAndGet();
        searchState.setValue(SearchState.idle(""));
        pinState.setValue(null);
    }

    private void startSearch(final String query) {
        final int id = searchGeneration.incrementAndGet();
        final int size = resultSize;
        searchState.setValue(SearchState.loading(query));
        executor.execute(new Runnable() {
            @Override
            public void run() {
                SearchState next;
                try {
                    List<GeoPlace> items = api.search(query, size);
                    List<GeoPlace> outside = new ArrayList<GeoPlace>();
                    for (GeoPlace p : items) {
                        if (isOutOfArea(p.location)) {
                            outside.add(p);
                        }
                    }
                    next = SearchState.result(query, items, outside);
                } catch (final ApiException e) {
                    Log.w(TAG, "주소 검색 실패: " + e.code, e);
                    if (ApiException.RATE_LIMITED.equals(e.code)) {
                        final long until = SystemClock.elapsedRealtime()
                                + Math.max(1, e.retryAfterSeconds) * 1000L;
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                rateLimitedUntil = until;
                            }
                        });
                    }
                    next = SearchState.error(query, e.getMessage());
                } catch (RuntimeException e) {
                    Log.e(TAG, "주소 검색 오류", e);
                    next = SearchState.error(query, "검색하지 못했어요. 잠시 후 다시 시도해 주세요.");
                }
                if (id == searchGeneration.get()) {
                    searchState.postValue(next);
                }
            }
        });
    }

    /** 백그라운드에서 부른다. 지하철 데이터를 못 읽으면 막지 않는다(우리 쪽 오류로 사용자를 막지 않기 위해). */
    private boolean isOutOfArea(LatLng location) {
        try {
            TransitData data = transit.get();
            return !ServiceArea.isSupported(data.index, location);
        } catch (Exception e) {
            Log.w(TAG, "지원 지역 확인 실패", e);
            return false;
        }
    }

    private void cancelPending() {
        if (pendingSearch != null) {
            main.removeCallbacks(pendingSearch);
            pendingSearch = null;
        }
    }

    /** 앞뒤 공백 제거, 연속 공백은 하나로. */
    static String normalize(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ");
    }

    /** @return 문제가 없으면 null, 있으면 사용자에게 보여 줄 문장 */
    static String validate(String query) {
        if (query.length() < GeocodeApi.MIN_QUERY_LENGTH) {
            return "검색어를 " + GeocodeApi.MIN_QUERY_LENGTH + "글자 이상 입력해 주세요.";
        }
        if (query.length() > GeocodeApi.MAX_QUERY_LENGTH) {
            return "검색어는 " + GeocodeApi.MAX_QUERY_LENGTH + "글자까지 입력할 수 있어요.";
        }
        return null;
    }

    @Override
    protected void onCleared() {
        cancelPending();
        executor.shutdownNow();
    }
}
