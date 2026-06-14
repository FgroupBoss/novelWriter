package com.novelwriter.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novelwriter.mapper.JobMapper;
import com.novelwriter.model.entity.JobEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private static final AtomicLong JOB_SEQ = new AtomicLong(0);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JobMapper jobMapper;
    private final ObjectMapper objectMapper;
    private final JobRunner jobRunner;

    private final ConcurrentHashMap<String, Boolean> pauseFlags = new ConcurrentHashMap<String, Boolean>();

    @Transactional
    public String submitTask(String projectId, String jobType, String label, TaskPayload payload) {
        String id = "j" + JOB_SEQ.incrementAndGet() + Long.toString(System.currentTimeMillis(), 36);
        JobEntity job = new JobEntity();
        job.setId(id);
        job.setProjectId(projectId);
        job.setJobType(jobType);
        job.setLabel(label);
        job.setStatus("running");
        job.setProgressStep(0);
        job.setProgressTotal(0);
        job.setProgressLabel("准备中");
        job.setCreatedAt(LocalDateTime.now());
        try {
            job.setTaskPayloadJson(objectMapper.writeValueAsString(payload));
            List<String> logs = new ArrayList<String>();
            logs.add(timestamp() + " 开始: " + label);
            job.setLogs(objectMapper.writeValueAsString(logs));
        } catch (Exception e) {
            job.setLogs("[]");
        }
        pauseFlags.remove(id);
        jobMapper.insert(job);
        scheduleExecution(id);
        return id;
    }

    /** 事务提交后再异步执行，避免 @Async 线程读不到未提交的 job 记录。 */
    private void scheduleExecution(String jobId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    jobRunner.execute(jobId);
                }
            });
        } else {
            jobRunner.execute(jobId);
        }
    }

    public boolean isPauseRequested(String jobId) {
        return Boolean.TRUE.equals(pauseFlags.get(jobId));
    }

    @Transactional
    public void appendLog(String jobId, String message) {
        JobEntity job = jobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        try {
            List<String> logs = readLogs(job);
            logs.add(timestamp() + " " + message);
            job.setLogs(objectMapper.writeValueAsString(logs));
            jobMapper.updateById(job);
        } catch (Exception e) {
            log.warn("追加任务日志失败 jobId={}", jobId, e);
        }
    }

    @Transactional
    public void updateProgress(String jobId, int step, int total, String label) {
        JobEntity job = jobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        job.setProgressStep(step);
        job.setProgressTotal(total);
        job.setProgressLabel(label);
        jobMapper.updateById(job);
    }

    @Transactional
    public boolean pauseJob(String jobId) {
        JobEntity job = jobMapper.selectById(jobId);
        if (job == null || !"running".equals(job.getStatus())) {
            return false;
        }
        pauseFlags.put(jobId, true);
        appendLog(jobId, "收到暂停指令，将在当前步骤完成后暂停…");
        return true;
    }

    @Transactional
    public boolean resumeJob(String jobId) {
        JobEntity job = jobMapper.selectById(jobId);
        if (job == null || !"paused".equals(job.getStatus())) {
            return false;
        }
        job.setStatus("running");
        job.setPausedAt(null);
        pauseFlags.remove(jobId);
        jobMapper.updateById(job);
        appendLog(jobId, "任务已继续");
        scheduleExecution(jobId);
        return true;
    }

    @Transactional
    public void markPaused(String jobId, int stepIndex) {
        JobEntity job = jobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        job.setStatus("paused");
        job.setProgressStep(stepIndex);
        job.setPausedAt(LocalDateTime.now());
        pauseFlags.remove(jobId);
        appendLog(jobId, "任务已暂停（步骤 " + stepIndex + "/" + job.getProgressTotal() + "）");
        jobMapper.updateById(job);
    }

    @Transactional
    public void markSuccess(String jobId, Map<String, Object> result) {
        JobEntity job = jobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        try {
            job.setStatus("success");
            job.setResultJson(objectMapper.writeValueAsString(result));
            appendLog(jobId, "任务完成");
            job.setFinishedAt(LocalDateTime.now());
            jobMapper.updateById(job);
        } catch (Exception e) {
            log.error("标记任务成功失败", e);
        }
    }

    @Transactional
    public void markFailed(String jobId, String error) {
        JobEntity job = jobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        job.setStatus("failed");
        job.setErrorMsg(error);
        appendLog(jobId, "失败: " + error);
        job.setFinishedAt(LocalDateTime.now());
        pauseFlags.remove(jobId);
        jobMapper.updateById(job);
    }

    public TaskPayload loadPayload(String jobId) {
        JobEntity job = jobMapper.selectById(jobId);
        if (job == null || job.getTaskPayloadJson() == null) {
            return TaskPayload.builder().build();
        }
        try {
            return objectMapper.readValue(job.getTaskPayloadJson(), TaskPayload.class);
        } catch (Exception e) {
            log.warn("任务 payload 反序列化失败 jobId={}", jobId, e);
            return TaskPayload.builder().build();
        }
    }

    public List<Map<String, Object>> listJobs(String projectId) {
        List<JobEntity> jobs = jobMapper.selectList(null);
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (JobEntity job : jobs) {
            if (projectId != null && !projectId.equals(job.getProjectId())) {
                continue;
            }
            result.add(toMap(job));
        }
        result.sort(new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> a, Map<String, Object> b) {
                return String.valueOf(b.get("created_at")).compareTo(String.valueOf(a.get("created_at")));
            }
        });
        if (result.size() > 50) {
            return result.subList(0, 50);
        }
        return result;
    }

    public Map<String, Object> getJob(String jobId) {
        JobEntity job = jobMapper.selectById(jobId);
        if (job == null) {
            return null;
        }
        return toMap(job);
    }

    @Transactional
    public boolean deleteJob(String jobId) {
        pauseFlags.remove(jobId);
        return jobMapper.deleteById(jobId) > 0;
    }

    private Map<String, Object> toMap(JobEntity job) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("id", job.getId());
        map.put("project_id", job.getProjectId());
        map.put("job_type", job.getJobType());
        map.put("label", job.getLabel());
        map.put("status", job.getStatus());
        map.put("error", job.getErrorMsg());
        map.put("progress_step", job.getProgressStep() != null ? job.getProgressStep() : 0);
        map.put("progress_total", job.getProgressTotal() != null ? job.getProgressTotal() : 0);
        map.put("progress_label", job.getProgressLabel());
        map.put("created_at", job.getCreatedAt() != null ? job.getCreatedAt().toString() : null);
        map.put("finished_at", job.getFinishedAt() != null ? job.getFinishedAt().toString() : null);
        map.put("paused_at", job.getPausedAt() != null ? job.getPausedAt().toString() : null);
        try {
            if (job.getLogs() != null) {
                map.put("logs", objectMapper.readValue(job.getLogs(), new TypeReference<List<String>>() {}));
            }
            if (job.getResultJson() != null) {
                map.put("result", objectMapper.readValue(job.getResultJson(), new TypeReference<Map<String, Object>>() {}));
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    private List<String> readLogs(JobEntity job) throws Exception {
        if (job.getLogs() == null || job.getLogs().isEmpty()) {
            return new ArrayList<String>();
        }
        return new ArrayList<String>(objectMapper.readValue(job.getLogs(), new TypeReference<List<String>>() {}));
    }

    private String timestamp() {
        return LocalDateTime.now().format(TIME_FMT);
    }
}
