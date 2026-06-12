package com.novelwriter.controller;

import com.novelwriter.service.DocumentService;
import com.novelwriter.service.DryRunService;
import com.novelwriter.service.NovelConfigService;
import com.novelwriter.service.ProjectService;
import com.novelwriter.service.WorkflowStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final DocumentService documentService;
    private final DryRunService dryRunService;
    private final WorkflowStateService workflowStateService;
    private final NovelConfigService novelConfigService;

    @GetMapping
    public List<Map<String, Object>> listProjects() {
        return projectService.listProjects();
    }

    @PostMapping
    public Map<String, Object> createProject(@RequestBody Map<String, String> body) throws Exception {
        String id = body.get("id");
        String title = body.get("title");
        return projectService.createProject(id, title);
    }

    @GetMapping("/{id}")
    public Map<String, Object> getProject(@PathVariable String id) {
        return projectService.getProjectDetail(id);
    }

    @GetMapping("/{id}/novel-config")
    public Map<String, Object> getNovelConfig(@PathVariable String id) {
        return novelConfigService.getNovelConfig(id);
    }

    @PutMapping("/{id}/novel-config")
    public Map<String, Object> updateNovelConfig(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return novelConfigService.updateNovelConfig(id, body);
    }

    @GetMapping("/{id}/context-preview")
    public Map<String, Object> contextPreview(
            @PathVariable String id,
            @RequestParam String stage,
            @RequestParam(required = false) Integer chapter) throws Exception {
        return dryRunService.buildContextPreview(id, stage, chapter);
    }

    @GetMapping("/{id}/stages/{stageId}")
    public Map<String, Object> getStage(
            @PathVariable String id,
            @PathVariable String stageId,
            @RequestParam(required = false) Integer chapter) throws Exception {
        Map<String, Object> preview = dryRunService.buildContextPreview(id, stageId, chapter);
        Map<String, Object> state = workflowStateService.loadState(id);
        List<String> completed = workflowStateService.getCompletedStages(state);
        preview.put("done", completed.contains(stageId));
        return preview;
    }

    @GetMapping("/{id}/idea-check")
    public Map<String, Object> ideaCheck(@PathVariable String id) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        try {
            workflowStateService.ensureIdeaFilled(id);
            result.put("ready", true);
            result.put("message", "创意已填写，可以一键启动");
        } catch (Exception e) {
            result.put("ready", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @GetMapping("/{id}/files")
    public List<Map<String, String>> listFiles(@PathVariable String id) {
        return documentService.buildFileTree(id);
    }

    @GetMapping("/{id}/file")
    public Map<String, String> readFile(@PathVariable String id, @RequestParam String path) {
        String content = documentService.readContent(id, path);
        if (content == null) {
            throw new IllegalArgumentException("文件不存在");
        }
        Map<String, String> result = new LinkedHashMap<String, String>();
        result.put("path", path);
        result.put("content", content);
        return result;
    }

    @PutMapping("/{id}/file")
    public Map<String, Object> writeFile(
            @PathVariable String id,
            @RequestParam String path,
            @RequestBody Map<String, String> body) {
        String content = body.get("content");
        documentService.writeFile(id, path, content != null ? content : "");
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("path", path);
        result.put("size", content != null ? content.getBytes().length : 0);
        return result;
    }
}
