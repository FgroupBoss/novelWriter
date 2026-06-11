package com.novelwriter.controller;

import com.novelwriter.service.JobService;
import com.novelwriter.service.TaskPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RunController {

    private final JobService jobService;

    @PostMapping("/projects/{id}/run/dry-run")
    public Map<String, String> dryRun(@PathVariable String id) {
        String jobId = jobService.submitTask(id, "dry_run", "试运行报告",
                TaskPayload.builder().kind("dry_run").build());
        return Collections.singletonMap("job_id", jobId);
    }

    @PostMapping("/projects/{id}/run/quickstart")
    public Map<String, String> quickstart(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        boolean dryRun = body != null && Boolean.TRUE.equals(body.get("dry_run"));
        if (dryRun) {
            String jobId = jobService.submitTask(id, "quickstart", "一键试运行",
                    TaskPayload.builder().kind("dry_run").build());
            return Collections.singletonMap("job_id", jobId);
        }
        String jobId = jobService.submitTask(id, "quickstart", "一键 · Idea → 第1章",
                TaskPayload.builder().kind("quickstart").build());
        return Collections.singletonMap("job_id", jobId);
    }

    @PostMapping("/projects/{id}/run/setup")
    public Map<String, String> runSetup(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        String stage = body != null && body.get("stage") != null ? String.valueOf(body.get("stage")) : null;
        boolean dryRun = body != null && Boolean.TRUE.equals(body.get("dry_run"));
        String label = stage != null ? stage : "全部设定";
        TaskPayload.TaskPayloadBuilder builder = TaskPayload.builder().dryRun(dryRun);
        if (stage != null) {
            builder.kind("setup_one").stage(stage);
        } else {
            builder.kind("setup_all");
        }
        String jobId = jobService.submitTask(id, "setup", label, builder.build());
        return Collections.singletonMap("job_id", jobId);
    }

    @PostMapping("/projects/{id}/run/chapter")
    public Map<String, String> runChapter(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        Integer chapter = body != null && body.get("chapter") != null ? ((Number) body.get("chapter")).intValue() : null;
        Integer fromChapter = body != null && body.get("from_chapter") != null ? ((Number) body.get("from_chapter")).intValue() : null;
        Integer toChapter = body != null && body.get("to_chapter") != null ? ((Number) body.get("to_chapter")).intValue() : null;
        String stage = body != null && body.get("stage") != null ? String.valueOf(body.get("stage")) : null;
        boolean dryRun = body != null && Boolean.TRUE.equals(body.get("dry_run"));

        TaskPayload.TaskPayloadBuilder builder = TaskPayload.builder().dryRun(dryRun);
        String label;
        if (stage != null && chapter != null) {
            builder.kind("chapter_one").stage(stage).chapter(chapter);
            label = "第" + chapter + "章 · " + stage;
        } else if (chapter != null) {
            builder.kind("chapter_loop").chapter(chapter);
            label = "第" + chapter + "章";
        } else if (fromChapter != null && toChapter != null) {
            builder.kind("chapter_range").fromChapter(fromChapter).toChapter(toChapter);
            label = "第" + fromChapter + "-" + toChapter + "章";
        } else {
            throw new IllegalArgumentException("缺少 chapter 参数");
        }
        String jobId = jobService.submitTask(id, "chapter", label, builder.build());
        return Collections.singletonMap("job_id", jobId);
    }

    @PostMapping("/projects/{id}/run/pipeline")
    public Map<String, String> runPipeline(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        boolean setupOnly = body != null && Boolean.TRUE.equals(body.get("setup_only"));
        boolean skipSetup = body != null && Boolean.TRUE.equals(body.get("skip_setup"));
        String chapters = body != null && body.get("chapters") != null ? String.valueOf(body.get("chapters")) : null;
        boolean dryRun = body != null && Boolean.TRUE.equals(body.get("dry_run"));

        String jobId = jobService.submitTask(id, "pipeline", "流水线",
                TaskPayload.builder()
                        .kind("pipeline")
                        .setupOnly(setupOnly)
                        .skipSetup(skipSetup)
                        .chapters(chapters)
                        .dryRun(dryRun)
                        .build());
        return Collections.singletonMap("job_id", jobId);
    }

    @GetMapping("/jobs")
    public List<Map<String, Object>> listJobs(@RequestParam(required = false) String project_id) {
        return jobService.listJobs(project_id);
    }

    @GetMapping("/jobs/{jobId}")
    public Map<String, Object> getJob(@PathVariable String jobId) {
        Map<String, Object> job = jobService.getJob(jobId);
        if (job == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        return job;
    }

    @PostMapping("/jobs/{jobId}/pause")
    public Map<String, Object> pauseJob(@PathVariable String jobId) {
        if (!jobService.pauseJob(jobId)) {
            throw new IllegalArgumentException("任务无法暂停（仅运行中任务可暂停）");
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("ok", true);
        return result;
    }

    @PostMapping("/jobs/{jobId}/resume")
    public Map<String, Object> resumeJob(@PathVariable String jobId) {
        if (!jobService.resumeJob(jobId)) {
            throw new IllegalArgumentException("任务无法继续（仅已暂停任务可继续）");
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("ok", true);
        return result;
    }

    @DeleteMapping("/jobs/{jobId}")
    public Map<String, Object> deleteJob(@PathVariable String jobId) {
        if (!jobService.deleteJob(jobId)) {
            throw new IllegalArgumentException("任务不存在");
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("ok", true);
        return result;
    }
}
