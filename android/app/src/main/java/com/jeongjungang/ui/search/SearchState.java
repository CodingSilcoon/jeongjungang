package com.jeongjungang.ui.search;

import com.jeongjungang.data.remote.GeoPlace;
import java.util.Collections;
import java.util.List;

/** 주소 검색 목록 상태. {@link AddressSearchViewModel#getSearchState()}로 관찰한다. */
public final class SearchState {

    public enum Status {
        /** 검색어가 없거나 너무 짧음. 목록을 비운다. */
        IDLE,
        LOADING,
        SUCCESS,
        /** 검색은 됐지만 결과가 없음. */
        EMPTY,
        ERROR
    }

    public final Status status;
    /** 이 상태를 만든 검색어(앞뒤 공백 제거). */
    public final String query;
    public final List<GeoPlace> items;
    /** ERROR일 때 사용자에게 보여 줄 문장. */
    public final String errorMessage;

    private SearchState(Status status, String query, List<GeoPlace> items, String errorMessage) {
        this.status = status;
        this.query = query;
        this.items = items;
        this.errorMessage = errorMessage;
    }

    static SearchState idle(String query) {
        return new SearchState(Status.IDLE, query, Collections.<GeoPlace>emptyList(), null);
    }

    static SearchState loading(String query) {
        return new SearchState(Status.LOADING, query, Collections.<GeoPlace>emptyList(), null);
    }

    static SearchState result(String query, List<GeoPlace> items) {
        return new SearchState(items.isEmpty() ? Status.EMPTY : Status.SUCCESS, query,
                Collections.unmodifiableList(items), null);
    }

    static SearchState error(String query, String message) {
        return new SearchState(Status.ERROR, query, Collections.<GeoPlace>emptyList(), message);
    }
}
