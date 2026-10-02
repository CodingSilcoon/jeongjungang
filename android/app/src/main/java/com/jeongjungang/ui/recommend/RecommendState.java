package com.jeongjungang.ui.recommend;

import com.jeongjungang.domain.model.LatLng;
import com.jeongjungang.domain.recommend.Criterion;
import com.jeongjungang.domain.recommend.Participant;
import com.jeongjungang.domain.recommend.Recommendation;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 추천 화면이 그릴 상태 한 장. {@link RecommendViewModel#getState()}로 관찰한다. */
public final class RecommendState {

    public enum Status {
        /** 아직 추천을 요청하지 않음. */
        IDLE,
        LOADING,
        /** 후보가 1곳 이상. */
        SUCCESS,
        /** 전원이 함께 갈 수 있는 후보가 없음. fallback 문구를 보여 준다. */
        EMPTY,
        ERROR
    }

    public final Status status;
    /** 요청에 쓴 참가자. 결과의 trips와 같은 순서다. */
    public final List<Participant> participants;
    public final Criterion criterion;
    /** 순위 순서. SUCCESS가 아니면 빈 목록. */
    public final List<Recommendation> recommendations;
    /** 후보 역 이름 → 좌표. 지도 마커와 길찾기 링크에 쓴다. */
    public final Map<String, LatLng> stationCoordinates;
    /** ERROR일 때 사용자에게 보여 줄 문장. */
    public final String errorMessage;

    private RecommendState(Status status, List<Participant> participants, Criterion criterion,
                           List<Recommendation> recommendations, Map<String, LatLng> stationCoordinates,
                           String errorMessage) {
        this.status = status;
        this.participants = participants;
        this.criterion = criterion;
        this.recommendations = recommendations;
        this.stationCoordinates = stationCoordinates;
        this.errorMessage = errorMessage;
    }

    static RecommendState idle() {
        return new RecommendState(Status.IDLE, Collections.<Participant>emptyList(), null,
                Collections.<Recommendation>emptyList(), Collections.<String, LatLng>emptyMap(), null);
    }

    static RecommendState loading(List<Participant> participants, Criterion criterion) {
        return new RecommendState(Status.LOADING, participants, criterion,
                Collections.<Recommendation>emptyList(), Collections.<String, LatLng>emptyMap(), null);
    }

    static RecommendState result(List<Participant> participants, Criterion criterion,
                                 List<Recommendation> recommendations, Map<String, LatLng> stationCoordinates) {
        Status status = recommendations.isEmpty() ? Status.EMPTY : Status.SUCCESS;
        return new RecommendState(status, participants, criterion,
                Collections.unmodifiableList(recommendations), Collections.unmodifiableMap(stationCoordinates), null);
    }

    static RecommendState error(List<Participant> participants, Criterion criterion, String message) {
        return new RecommendState(Status.ERROR, participants, criterion,
                Collections.<Recommendation>emptyList(), Collections.<String, LatLng>emptyMap(), message);
    }
}
