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
            return;
        }
        if (!"running".equals(job.getStatus())) {
            return;
        }
        jobExecutorService.execute(jobId);
    }
}
