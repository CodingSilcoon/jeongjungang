package com.jeongjungang.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * DB 커밋이 끝난 뒤에 할 일(푸시 보내기, Redis 큐 고치기). 롤백되면 하지 않는다.
 * 트랜잭션 밖에서 부르면 바로 한다. 실패해도 이미 커밋된 요청을 실패로 만들지 않고 로그만 남긴다.
 */
public final class AfterCommit {

    private static final Logger log = LoggerFactory.getLogger(AfterCommit.class);

    private AfterCommit() {
    }

    public static void run(String what, Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runSafely(what, task);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runSafely(what, task);
            }
        });
    }

    private static void runSafely(String what, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            log.warn("커밋 뒤 작업에 실패했습니다: {}", what, e);
        }
    }
}
