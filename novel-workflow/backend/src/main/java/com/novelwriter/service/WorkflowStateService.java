package com.novelwriter.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novelwriter.common.AssistException;
import com.novelwriter.config.StageConfigService;
import com.novelwriter.mapper.ProjectMapper;
import com.novelwriter.model.entity.ProjectEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * workflow_state.json 读写（MySQL 持久化）。
 */
@Service
@RequiredArgsConstructor
public class WorkflowStateService {

    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private final ProjectMapper projectMapper;
    private final StageConfigService stageConfigService;
    private final ObjectMapper objectMapper;
    private final DocumentService documentService;

    public Map<String, Object> loadState(String projectId) {
        ProjectEntity project = requireProject(projectId);
        if (project.getStateJson() != null && !project.getStateJson().isEmpty()) {
            try {
                return objectMapper.readValue(project.getStateJson(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                return buildDefaultState(project);
            }
        }
        return buildDefaultState(project);
    }

    @Transactional
    public void saveState(String projectId, Map<String, Object> state) {
        ProjectEntity project = requireProject(projectId);
        state.put("last_updated", ISO_FORMAT.format(LocalDateTime.now(ZoneOffset.UTC)));
        try {
            project.setStateJson(objectMapper.writeValueAsString(state));
            project.setCurrentStage(String.valueOf(state.getOrDefault("current_stage", "idea")));
            Object ch = state.get("current_chapter");
            project.setCurrentChapter(ch instanceof Number ? ((Number) ch).intValue() : 0);
            Object completed = state.get("completed_stages");
            if (completed != null) {
                project.setCompletedStages(objectMapper.writeValueAsString(completed));
            }
            project.setUpdatedAt(LocalDateTime.now());
            projectMapper.updateById(project);
            documentService.writeFile(projectId, "meta/workflow_state.json",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(state));
        } catch (Exception e) {
            throw new AssistException("保存工作流状态失败: " + e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> markStageComplete(String projectId, String stage, Integer chapter) {
        Map<String, Object> state = loadState(projectId);
        List<String> completed = getCompletedStages(state);
        if (!completed.contains(stage)) {
            completed.add(stage);
        }
        state.put("completed_stages", completed);
        state.put("current_stage", stage);
        if (chapter != null) {
            state.put("current_chapter", chapter);
            updateVolumeProgress(state, chapter);
        }
        saveState(projectId, state);
        return state;
    }

    public void ensureIdeaFilled(String projectId) {
        String text = documentService.readContent(projectId, "00_idea.md");
        if (text == null || text.isEmpty()) {
            throw new AssistException("缺少创意文件: 00_idea.md");
        }
        if (text.contains("（例：") && text.contains("## 一句话梗概")) {
            String section = text.split("## 一句话梗概", 2)[1].split("##", 2)[0];
            boolean hasContent = false;
            for (String line : section.split("\n")) {
                String ln = line.trim();
                if (!ln.isEmpty() && !ln.startsWith("（例：")) {
                    hasContent = true;
                    break;
                }
            }
            if (!hasContent) {
                throw new AssistException("请先在 00_idea.md 中填写「一句话梗概」后再运行");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void updateVolumeProgress(Map<String, Object> state, int chapter) {
        Object volumesObj = state.get("volumes");
        if (!(volumesObj instanceof Map)) {
            return;
        }
        Map<String, Object> volumes = (Map<String, Object>) volumesObj;
        for (Object volObj : volumes.values()) {
            if (!(volObj instanceof Map)) {
                continue;
            }
            Map<String, Object> vol = (Map<String, Object>) volObj;
            int start = toInt(vol.get("chapter_start"));
            int end = toInt(vol.get("chapter_end"));
            if (start <= chapter && chapter <= end) {
                vol.put("chapters_done", chapter - start + 1);
                break;
            }
        }
    }

    private int toInt(Object val) {
        return val instanceof Number ? ((Number) val).intValue() : 0;
    }

    @SuppressWarnings("unchecked")
    public List<String> getCompletedStages(Map<String, Object> state) {
        Object completed = state.get("completed_stages");
        if (completed instanceof List) {
            return new ArrayList<String>((List<String>) completed);
        }
        return new ArrayList<String>();
    }

    private Map<String, Object> buildDefaultState(ProjectEntity project) {
        Map<String, Object> state = new LinkedHashMap<String, Object>();
        state.put("novel_id", project.getId());
        state.put("title", project.getTitle());
        state.put("current_stage", project.getCurrentStage());
        state.put("current_chapter", project.getCurrentChapter());
        return state;
    }

    private ProjectEntity requireProject(String projectId) {
        ProjectEntity project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new AssistException("项目不存在: " + projectId);
        }
        return project;
    }
}
