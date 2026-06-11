package com.novelwriter.service;

import com.novelwriter.common.AssistException;
import lombok.Getter;

/**
 * 任务执行上下文：日志、进度、暂停检查。
 */
@Getter
public class JobContext {

    private final String jobId;
    private final String projectId;
    private final JobService jobService;

    public JobContext(String jobId, String projectId, JobService jobService) {
        this.jobId = jobId;
        this.projectId = projectId;
        this.jobService = jobService;
    }

    public void log(String message) {
        jobService.appendLog(jobId, message);
    }

    public void updateProgress(int step, int total, String label) {
        jobService.updateProgress(jobId, step, total, label);
    }

    public void checkPause() {
        if (jobService.isPauseRequested(jobId)) {
            throw new JobPausedException("任务已暂停");
        }
    }
}
