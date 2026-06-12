package com.novelwriter.service;

import com.novelwriter.common.AssistException;
import com.novelwriter.config.StageConfigService;
import com.novelwriter.mapper.ProjectMapper;
import com.novelwriter.model.entity.ProjectEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 小说维度配置（每项目独立，默认值来自 config.yaml）。
 */
@Service
@RequiredArgsConstructor
public class NovelConfigService {

    private final ProjectMapper projectMapper;
    private final StageConfigService stageConfigService;
    private final WorkflowStateService workflowStateService;

    public Map<String, Object> getDefaults() {
        return stageConfigService.getDefaultNovelConfig();
    }

    public Map<String, Object> getNovelConfig(String projectId) {
        ProjectEntity project = requireProject(projectId);
        return toMap(project);
    }

    public void applyDefaults(ProjectEntity project) {
        Map<String, Object> defaults = getDefaults();
        project.setLanguage(stringVal(defaults.get("language"), "zh-CN"));
        project.setScale(stringVal(defaults.get("scale"), "long"));
        project.setTargetChapters(intVal(defaults.get("target_chapters"), 80));
        project.setChaptersPerVolume(intVal(defaults.get("chapters_per_volume"), 20));
        project.setWordsPerChapter(intVal(defaults.get("words_per_chapter"), 3000));
        project.setSummaryMaxChars(intVal(defaults.get("summary_max_chars"), 400));
        project.setPlotProgressMaxChars(intVal(defaults.get("plot_progress_max_chars"), 2000));
        project.setPrevChapterSummaryChars(intVal(defaults.get("prev_chapter_summary_chars"), 600));
    }

    @Transactional
    public Map<String, Object> updateNovelConfig(String projectId, Map<String, Object> body) {
        ProjectEntity project = requireProject(projectId);
        if (body.containsKey("language")) {
            project.setLanguage(requireNonEmpty(stringVal(body.get("language"), project.getLanguage()), "language"));
        }
        if (body.containsKey("scale")) {
            project.setScale(requireNonEmpty(stringVal(body.get("scale"), project.getScale()), "scale"));
        }
        if (body.containsKey("target_chapters")) {
            project.setTargetChapters(clamp(intVal(body.get("target_chapters"), project.getTargetChapters()), 1, 9999));
        }
        if (body.containsKey("chapters_per_volume")) {
            project.setChaptersPerVolume(clamp(intVal(body.get("chapters_per_volume"), project.getChaptersPerVolume()), 1, 500));
        }
        if (body.containsKey("words_per_chapter")) {
            project.setWordsPerChapter(clamp(intVal(body.get("words_per_chapter"), project.getWordsPerChapter()), 500, 50000));
        }
        if (body.containsKey("summary_max_chars")) {
            project.setSummaryMaxChars(clamp(intVal(body.get("summary_max_chars"), project.getSummaryMaxChars()), 100, 5000));
        }
        if (body.containsKey("plot_progress_max_chars")) {
            project.setPlotProgressMaxChars(clamp(intVal(body.get("plot_progress_max_chars"), project.getPlotProgressMaxChars()), 500, 50000));
        }
        if (body.containsKey("prev_chapter_summary_chars")) {
            project.setPrevChapterSummaryChars(clamp(intVal(body.get("prev_chapter_summary_chars"), project.getPrevChapterSummaryChars()), 100, 5000));
        }
        projectMapper.updateById(project);
        syncWorkflowState(project);
        return toMap(project);
    }

    @Transactional
    public void backfillAllProjects() {
        for (ProjectEntity project : projectMapper.selectList(null)) {
            if (project.getId().startsWith("_")) {
                continue;
            }
            adoptFromWorkflowStateIfPresent(project);
            if (project.getTargetChapters() == null || project.getTargetChapters() <= 0) {
                applyDefaults(project);
            }
            projectMapper.updateById(project);
            syncWorkflowState(project);
        }
    }

    public void syncWorkflowStateForProject(String projectId) {
        syncWorkflowState(requireProject(projectId));
    }

    private void adoptFromWorkflowStateIfPresent(ProjectEntity project) {
        Map<String, Object> state = workflowStateService.loadState(project.getId());
        Object tc = state.get("target_chapters");
        if (tc instanceof Number) {
            int fromState = ((Number) tc).intValue();
            if (fromState > 0) {
                project.setTargetChapters(fromState);
            }
        }
    }

    public Map<String, Object> toMap(ProjectEntity project) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("language", stringOrDefault(project.getLanguage(), "zh-CN"));
        map.put("scale", stringOrDefault(project.getScale(), "long"));
        map.put("target_chapters", nullToDefault(project.getTargetChapters(), 80));
        map.put("chapters_per_volume", nullToDefault(project.getChaptersPerVolume(), 20));
        map.put("words_per_chapter", nullToDefault(project.getWordsPerChapter(), 3000));
        map.put("summary_max_chars", nullToDefault(project.getSummaryMaxChars(), 400));
        map.put("plot_progress_max_chars", nullToDefault(project.getPlotProgressMaxChars(), 2000));
        map.put("prev_chapter_summary_chars", nullToDefault(project.getPrevChapterSummaryChars(), 600));
        return map;
    }

    public int getTargetChapters(ProjectEntity project) {
        Integer tc = project.getTargetChapters();
        return tc != null && tc > 0 ? tc : stageConfigService.getDefaultTargetChapters();
    }

    private void syncWorkflowState(ProjectEntity project) {
        Map<String, Object> state = workflowStateService.loadState(project.getId());
        int target = getTargetChapters(project);
        int perVol = nullToDefault(project.getChaptersPerVolume(), 20);
        state.put("target_chapters", target);
        state.put("volumes", buildVolumes(state, target, perVol));
        workflowStateService.saveState(project.getId(), state);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildVolumes(Map<String, Object> state, int targetChapters, int chaptersPerVolume) {
        Map<String, Object> oldVolumes = state.get("volumes") instanceof Map
                ? (Map<String, Object>) state.get("volumes") : null;
        Map<String, Object> volumes = new LinkedHashMap<String, Object>();
        int volCount = Math.max(1, (int) Math.ceil((double) targetChapters / chaptersPerVolume));
        for (int i = 1; i <= volCount; i++) {
            int start = (i - 1) * chaptersPerVolume + 1;
            int end = Math.min(i * chaptersPerVolume, targetChapters);
            Map<String, Object> vol = new LinkedHashMap<String, Object>();
            String oldName = "";
            int chaptersDone = 0;
            if (oldVolumes != null) {
                Object oldVolObj = oldVolumes.get(String.valueOf(i));
                if (oldVolObj instanceof Map) {
                    Map<String, Object> oldVol = (Map<String, Object>) oldVolObj;
                    Object name = oldVol.get("name");
                    if (name != null) {
                        oldName = String.valueOf(name);
                    }
                    chaptersDone = intVal(oldVol.get("chapters_done"), 0);
                }
            }
            vol.put("name", oldName);
            vol.put("chapter_start", start);
            vol.put("chapter_end", end);
            vol.put("chapters_done", Math.min(chaptersDone, end - start + 1));
            volumes.put(String.valueOf(i), vol);
        }
        return volumes;
    }

    private ProjectEntity requireProject(String projectId) {
        ProjectEntity project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new AssistException("项目不存在: " + projectId);
        }
        return project;
    }

    private static String stringVal(Object val, String fallback) {
        return val != null ? String.valueOf(val).trim() : fallback;
    }

    private static int intVal(Object val, int fallback) {
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        if (val instanceof String) {
            try {
                return Integer.parseInt(((String) val).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static int nullToDefault(Integer val, int fallback) {
        return val != null ? val : fallback;
    }

    private static String stringOrDefault(String val, String fallback) {
        return val != null && !val.isEmpty() ? val : fallback;
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    private static String requireNonEmpty(String val, String field) {
        if (val == null || val.isEmpty()) {
            throw new AssistException(field + " 不能为空");
        }
        return val;
    }
}
