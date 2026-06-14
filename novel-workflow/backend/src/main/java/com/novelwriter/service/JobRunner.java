package com.novelwriter.service;

import com.novelwriter.mapper.JobMapper;
import com.novelwriter.model.entity.JobEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobRunner {

    private final JobMapper jobMapper;
    private final JobExecutorService jobExecutorService;

    @Async("jobExecutor")
    public void execute(String jobId) {
        JobEntity job = jobMapper.selectById(jobId);
        if (job == null) {
            log.error("任务不存在，无法执行 jobId={}", jobId);
            return;
        }
        if (!"running".equals(job.getStatus())) {
            log.warn("任务状态非 running，跳过执行 jobId={} status={}", jobId, job.getStatus());
            return;
        }
        log.info("开始执行任务 jobId={} projectId={} type={} label={}",
                jobId, job.getProjectId(), job.getJobType(), job.getLabel());
        jobExecutorService.execute(jobId);
    }
}
