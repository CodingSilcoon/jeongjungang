package com.jeongjungang.ui.meeting;

import com.jeongjungang.data.remote.meeting.MeetingModels.Snapshot;

/** 지금 보고 있는 약속의 상태. {@link MeetingViewModel#getState()}로 관찰한다. */
public final class MeetingState {

    public enum Status {
        /** 열린 약속이 없음. */
        IDLE,
        /** 처음 불러오는 중(아직 snapshot 없음). */
        LOADING,
        /** snapshot이 있음. 폴링으로 계속 갱신된다. */
        READY,
        /** 약속이 취소·만료됐거나 내가 빠짐. 목록 화면으로 돌아간다. */
        GONE,
        /** 처음 불러오기 실패(snapshot 없음). 다시 시도 버튼을 보여 준다. */
        ERROR
    }

    public final Status status;
    public final String meetingId;
    /** READY면 항상 있음. 그 밖에는 null. */
    public final Snapshot snapshot;
    /**
     * 사용자에게 보여 줄 문장. GONE·ERROR의 이유,
     * 또는 READY인데 마지막 갱신이 실패한 경우("연결이 불안정해요") 잠깐 띄울 안내.
     */
    public final String message;

    private MeetingState(Status status, String meetingId, Snapshot snapshot, String message) {
        this.status = status;
        this.meetingId = meetingId;
        this.snapshot = snapshot;
        this.message = message;
    }

    static MeetingState idle() {
        return new MeetingState(Status.IDLE, null, null, null);
    }

    static MeetingState loading(String meetingId) {
        return new MeetingState(Status.LOADING, meetingId, null, null);
    }

    static MeetingState ready(String meetingId, Snapshot snapshot, String staleMessage) {
        return new MeetingState(Status.READY, meetingId, snapshot, staleMessage);
    }

    static MeetingState gone(String meetingId, String message) {
        return new MeetingState(Status.GONE, meetingId, null, message);
    }

    static MeetingState error(String meetingId, String message) {
        return new MeetingState(Status.ERROR, meetingId, null, message);
    }
}
