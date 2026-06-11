package com.novelwriter.service;

import com.novelwriter.config.StageConfigService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class JobExecutorService {

    private static final ScheduledExecutorService HEARTBEAT_POOL =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "job-heartbeat");
                t.setDaemon(true);
                return t;
            });

    private final JobService jobService;
    private final StageRunnerService stageRunnerService;
    private final DryRunService dryRunService;
    private final StageConfigService stageConfigService;

    public JobExecutorService(@Lazy JobService jobService,
                              StageRunnerService stageRunnerService,
                              DryRunService dryRunService,
                              StageConfigService stageConfigService) {
        this.jobService = jobService;
        this.stageRunnerService = stageRunnerService;
        this.dryRunService = dryRunService;
        this.stageConfigService = stageConfigService;
    }

    public void execute(String jobId) {
        Map<String, Object> jobMap = jobService.getJob(jobId);
        if (jobMap == null) {
            return;
        }
        String projectId = String.valueOf(jobMap.get("project_id"));
        JobContext ctx = new JobContext(jobId, projectId, jobService);
        TaskPayload payload = jobService.loadPayload(jobId);
        List<ExecutionStep> steps = expandSteps(payload);

        int resumeFrom = jobMap.get("progress_step") != null ? ((Number) jobMap.get("progress_step")).intValue() : 0;
        if (resumeFrom >= steps.size()) {
            resumeFrom = 0;
        }

        jobService.updateProgress(jobId, resumeFrom, steps.size(),
                steps.isEmpty() ? "无步骤" : steps.get(resumeFrom).getLabel());

        ScheduledFuture<?> heartbeat = HEARTBEAT_POOL.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                Map<String, Object> j = jobService.getJob(jobId);
                if (j == null || !"running".equals(j.get("status"))) {
                    return;
                }
                int step = ((Number) j.get("progress_step")).intValue();
                int total = ((Number) j.get("progress_total")).intValue();
                String label = String.valueOf(j.get("progress_label"));
                jobService.appendLog(jobId, "[进度] " + step + "/" + total + " · " + label);
            }
        }, 5, 5, TimeUnit.SECONDS);

        int pausedAtStep = resumeFrom;
        try {
            for (int i = resumeFrom; i < steps.size(); i++) {
                pausedAtStep = i;
                ctx.checkPause();
                ExecutionStep step = steps.get(i);
                jobService.updateProgress(jobId, i + 1, steps.size(), step.getLabel());
                ctx.log("执行步骤 (" + (i + 1) + "/" + steps.size() + "): " + step.getLabel());
                runStep(ctx, payload, step);
            }
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("ok", true);
            result.put("steps", steps.size());
            jobService.markSuccess(jobId, result);
        } catch (JobPausedException e) {
            jobService.markPaused(jobId, pausedAtStep);
        } catch (Exception e) {
            log.error("任务执行失败 jobId={}", jobId, e);
            jobService.markFailed(jobId, e.getMessage());
        } finally {
            heartbeat.cancel(false);
        }
    }

    private void runStep(JobContext ctx, TaskPayload payload, ExecutionStep step) {
        String projectId = ctx.getProjectId();
        boolean dryRun = Boolean.TRUE.equals(payload.getDryRun());

        if ("dry_run".equals(step.getKind())) {
            dryRunService.buildDryRunReport(projectId);
            return;
        }
        if (step.isChapterStage()) {
            stageRunnerService.runChapterStage(projectId, step.getStageId(), step.getChapter(), dryRun, ctx);
            return;
        }
        stageRunnerService.runSetupStage(projectId, step.getStageId(), dryRun, ctx);
    }

    private List<ExecutionStep> expandSteps(TaskPayload payload) {
        List<ExecutionStep> steps = new ArrayList<ExecutionStep>();
        String kind = payload.getKind() != null ? payload.getKind() : "setup_all";
        boolean dryRun = Boolean.TRUE.equals(payload.getDryRun());

        if ("dry_run".equals(kind)) {
            steps.add(step("dry_run", null, null, false, "试运行报告"));
            return steps;
        }
        if ("setup_one".equals(kind) && payload.getStage() != null) {
            steps.add(step("setup", payload.getStage(), null, false, label(payload.getStage())));
            return steps;
        }
        if ("chapter_one".equals(kind) && payload.getStage() != null && payload.getChapter() != null) {
            steps.add(step("chapter", payload.getStage(), payload.getChapter(), true, chapterLabel(payload.getChapter(), payload.getStage())));
            return steps;
        }
        if ("chapter_loop".equals(kind) && payload.getChapter() != null) {
            for (String s : stageConfigService.getChapterLoop()) {
                steps.add(step("chapter", s, payload.getChapter(), true, chapterLabel(payload.getChapter(), s)));
            }
            return steps;
        }
        if ("chapter_range".equals(kind) && payload.getFromChapter() != null && payload.getToChapter() != null) {
            for (int ch = payload.getFromChapter(); ch <= payload.getToChapter(); ch++) {
                for (String s : stageConfigService.getChapterLoop()) {
                    steps.add(step("chapter", s, ch, true, chapterLabel(ch, s)));
                }
            }
            return steps;
        }
        if ("quickstart".equals(kind)) {
            if (!dryRun) {
                for (String s : stageConfigService.getSetupOrder()) {
                    steps.add(step("setup", s, null, false, label(s)));
                }
            }
            for (String s : stageConfigService.getChapterLoop()) {
                steps.add(step("chapter", s, 1, true, chapterLabel(1, s)));
            }
            return steps;
        }
        if ("pipeline".equals(kind)) {
            if (!Boolean.TRUE.equals(payload.getSkipSetup())) {
                for (String s : stageConfigService.getSetupOrder()) {
                    steps.add(step("setup", s, null, false, label(s)));
                }
            }
            if (!Boolean.TRUE.equals(payload.getSetupOnly()) && payload.getChapters() != null) {
                int from = parseChapterFrom(payload.getChapters());
                int to = parseChapterTo(payload.getChapters());
                for (int ch = from; ch <= to; ch++) {
                    for (String s : stageConfigService.getChapterLoop()) {
                        steps.add(step("chapter", s, ch, true, chapterLabel(ch, s)));
                    }
                }
            }
            return steps;
        }

        for (String s : stageConfigService.getSetupOrder()) {
            steps.add(step("setup", s, null, false, label(s)));
        }
        return steps;
    }

    private ExecutionStep step(String kind, String stageId, Integer chapter, boolean chapterStage, String label) {
        ExecutionStep s = new ExecutionStep();
        s.setKind(kind);
        s.setStageId(stageId);
        s.setChapter(chapter);
        s.setChapterStage(chapterStage);
        s.setLabel(label);
        return s;
    }

    private String label(String stageId) {
        return StageConfigService.STAGE_LABELS.getOrDefault(stageId, stageId);
    }

    private String chapterLabel(int chapter, String stageId) {
        return "第" + chapter + "章 · " + label(stageId);
    }

    private int parseChapterFrom(String chapters) {
        String[] parts = chapters.split("-");
        return Integer.parseInt(parts[0].trim());
    }

    private int parseChapterTo(String chapters) {
        String[] parts = chapters.split("-");
        return parts.length > 1 ? Integer.parseInt(parts[1].trim()) : parseChapterFrom(chapters);
    }

    @Data
    static class ExecutionStep {
        private String kind;
        private String stageId;
        private Integer chapter;
        private boolean chapterStage;
        private String label;
    }
}
