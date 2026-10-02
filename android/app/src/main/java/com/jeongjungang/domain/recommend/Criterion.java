package com.jeongjungang.domain.recommend;

/** 후보를 고르는 기준. */
public enum Criterion {
    /** 모두의 이동시간 합이 가장 짧은 곳. 분 단위로 같으면 환승이 적은 쪽, 그다음 최대 시간이 짧은 쪽. */
    TOTAL_TIME,
    /** 가장 오래 걸리는 사람의 시간이 가장 짧은 곳. 분 단위로 같으면 환승이 적은 쪽, 그다음 합이 짧은 쪽. */
    MAX_TIME
}
