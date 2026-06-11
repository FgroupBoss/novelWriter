package com.novelwriter.controller;

import com.novelwriter.service.ArtifactService;
import com.novelwriter.service.PromptRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}")
@RequiredArgsConstructor
public class ArtifactController {

    private final ArtifactService artifactService;
    private final PromptRecordService promptRecordService;

    @GetMapping("/artifacts/stats")
    public Map<String, Object> stats(@PathVariable String projectId) {
        return artifactService.getArtifactStats(projectId);
    }

    @GetMapping("/artifacts")
    public List<Map<String, Object>> list(
            @PathVariable String projectId,
            @RequestParam(required = false) String category) {
        return artifactService.listArtifacts(projectId, category);
    }

    @GetMapping("/artifacts/content")
    public Map<String, Object> content(
            @PathVariable String projectId,
            @RequestParam String path) {
        Map<String, Object> artifact = artifactService.getArtifact(projectId, path);
        if (artifact == null) {
            throw new IllegalArgumentException("产物不存在");
        }
        return artifact;
    }

    @GetMapping("/prompts")
    public List<Map<String, Object>> listPrompts(
            @PathVariable String projectId,
            @RequestParam(required = false) String prompt_type,
            @RequestParam(required = false) String job_id) {
        return promptRecordService.listByProject(projectId, prompt_type, job_id);
    }

    @GetMapping("/prompts/{promptId}")
    public Map<String, Object> getPrompt(@PathVariable String projectId, @PathVariable Long promptId) {
        Map<String, Object> record = promptRecordService.getDetail(promptId);
        if (record == null || !projectId.equals(record.get("project_id"))) {
            throw new IllegalArgumentException("Prompt 记录不存在");
        }
        return record;
    }
}
