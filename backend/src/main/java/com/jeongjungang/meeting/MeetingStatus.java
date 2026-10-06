package com.jeongjungang.meeting;

/** 약속 상태. 취소된 약속은 상태로 남기지 않고 데이터를 지운다. */
public enum MeetingStatus {
    OPEN, CONFIRMED
}
