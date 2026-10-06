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
    /**
     * 지원 지역 밖이라 추천하지 못한 참가자(입력 순서). 이 목록이 비어 있지 않으면 status는 ERROR이고,
     * 화면은 해당 참가자 출발지를 표시해 다시 고르게 하면 된다. 그 밖에는 빈 목록.
     */
    public final List<Participant> outOfArea;

    private RecommendState(Status status, List<Participant> participants, Criterion criterion,
                           List<Recommendation> recommendations, Map<String, LatLng> stationCoordinates,
                           String errorMessage, List<Participant> outOfArea) {
        this.status = status;
        this.participants = participants;
        this.criterion = criterion;
        this.recommendations = recommendations;
        this.stationCoordinates = stationCoordinates;
        this.errorMessage = errorMessage;
        this.outOfArea = outOfArea;
    }

    static RecommendState idle() {
        return new RecommendState(Status.IDLE, Collections.<Participant>emptyList(), null,
                Collections.<Recommendation>emptyList(), Collections.<String, LatLng>emptyMap(), null,
                Collections.<Participant>emptyList());
    }

    static RecommendState loading(List<Participant> participants, Criterion criterion) {
        return new RecommendState(Status.LOADING, participants, criterion,
                Collections.<Recommendation>emptyList(), Collections.<String, LatLng>emptyMap(), null,
                Collections.<Participant>emptyList());
    }

    static RecommendState result(List<Participant> participants, Criterion criterion,
                                 List<Recommendation> recommendations, Map<String, LatLng> stationCoordinates) {
        Status status = recommendations.isEmpty() ? Status.EMPTY : Status.SUCCESS;
        return new RecommendState(status, participants, criterion,
                Collections.unmodifiableList(recommendations), Collections.unmodifiableMap(stationCoordinates), null,
                Collections.<Participant>emptyList());
    }

    static RecommendState error(List<Participant> participants, Criterion criterion, String message) {
        return new RecommendState(Status.ERROR, participants, criterion,
                Collections.<Recommendation>emptyList(), Collections.<String, LatLng>emptyMap(), message,
                Collections.<Participant>emptyList());
    }

    static RecommendState outOfArea(List<Participant> participants, Criterion criterion,
                                    List<Participant> outside, String message) {
        return new RecommendState(Status.ERROR, participants, criterion,
                Collections.<Recommendation>emptyList(), Collections.<String, LatLng>emptyMap(), message,
                Collections.unmodifiableList(new java.util.ArrayList<Participant>(outside)));
    }
}
