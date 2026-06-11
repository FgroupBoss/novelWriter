package com.novelwriter.service;

/**
 * 任务暂停信号，由 JobExecutor 捕获并持久化 paused 状态。
 */
public class JobPausedException extends RuntimeException {

    public JobPausedException(String message) {
        super(message);
    }
}
