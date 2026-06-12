package com.novelwriter.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novelwriter.common.AssistException;
import com.novelwriter.config.StageConfigService;
import com.novelwriter.mapper.ProjectMapper;
import com.novelwriter.model.entity.ProjectEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目 CRUD。
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private static final String TEMPLATE_PREFIX = "_template/";

    private final ProjectMapper projectMapper;
    private final DocumentService documentService;
    private final WorkflowStateService workflowStateService;
    private final StageConfigService stageConfigService;
    private final ObjectMapper objectMapper;
    private final PromptRecordService promptRecordService;
    private final NovelConfigService novelConfigService;

    public List<Map<String, Object>> listProjects() {
        List<ProjectEntity> projects = projectMapper.selectList(null);
        List<String> setupOrder = stageConfigService.getSetupOrder();
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();

        for (ProjectEntity p : projects) {
            if (p.getId().startsWith("_")) {
                continue;
            }
            Map<String, Object> state = workflowStateService.loadState(p.getId());
            List<String> completed = workflowStateService.getCompletedStages(state);
            int setupDone = 0;
            for (String s : completed) {
                if (setupOrder.contains(s)) {
                    setupDone++;
                }
            }

            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", p.getId());
            item.put("title", p.getTitle() != null ? p.getTitle() : p.getId());
            item.put("current_stage", state.getOrDefault("current_stage", "idea"));
            item.put("current_chapter", state.getOrDefault("current_chapter", 0));
            item.put("completed_stages", completed);
            item.put("setup_done", setupDone);
            item.put("setup_total", setupOrder.size());
            item.put("target_chapters", novelConfigService.getTargetChapters(p));
            item.put("novel_config", novelConfigService.toMap(p));
            items.add(item);
        }
        return items;
    }

    @Transactional
    public Map<String, Object> createProject(String projectId, String title) throws IOException {
        documentService.validateProjectId(projectId);
        if (projectMapper.selectById(projectId) != null) {
            throw new AssistException("项目已存在: " + projectId);
        }

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setTitle(title != null ? title : projectId);
        novelConfigService.applyDefaults(project);
        project.setCurrentStage("idea");
        project.setCurrentChapter(0);
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.insert(project);

        importTemplateDocuments(projectId);
        documentService.ensureProjectBootstrapFiles(projectId);
        promptRecordService.seedProjectTemplates(projectId);
        documentService.backfillMetadata(projectId);

        if (title != null) {
            Map<String, Object> state = workflowStateService.loadState(projectId);
            state.put("title", title);
            state.put("novel_id", projectId);
            workflowStateService.saveState(projectId, state);
            project.setTitle(title);
            projectMapper.updateById(project);
        }
        novelConfigService.syncWorkflowStateForProject(projectId);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", projectId);
        result.put("title", title != null ? title : projectId);
        return result;
    }

    public Map<String, Object> getProjectDetail(String projectId) {
        ProjectEntity project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new AssistException("项目不存在: " + projectId);
        }

        Map<String, Object> state = workflowStateService.loadState(projectId);
        List<String> setupOrder = stageConfigService.getSetupOrder();
        List<String> completed = workflowStateService.getCompletedStages(state);

        List<Map<String, Object>> setupProgress = new ArrayList<Map<String, Object>>();
        for (String s : setupOrder) {
            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            entry.put("id", s);
            entry.put("label", StageConfigService.STAGE_LABELS.getOrDefault(s, s));
            entry.put("done", completed.contains(s));
            setupProgress.add(entry);
        }

        int setupDone = 0;
        for (Map<String, Object> sp : setupProgress) {
            if (Boolean.TRUE.equals(sp.get("done"))) {
                setupDone++;
            }
        }

        List<Map<String, String>> chapterLoop = new ArrayList<Map<String, String>>();
        for (String s : stageConfigService.getChapterLoop()) {
            Map<String, String> entry = new LinkedHashMap<String, String>();
            entry.put("id", s);
            entry.put("label", StageConfigService.STAGE_LABELS.getOrDefault(s, s));
            chapterLoop.add(entry);
        }

        Map<String, Object> detail = new LinkedHashMap<String, Object>();
        detail.put("id", projectId);
        detail.put("title", state.getOrDefault("title", project.getTitle()));
        detail.put("state", state);
        detail.put("setup_progress", setupProgress);
        detail.put("setup_done", setupDone);
        detail.put("setup_total", setupProgress.size());
        detail.put("target_chapters", novelConfigService.getTargetChapters(project));
        detail.put("novel_config", novelConfigService.toMap(project));
        detail.put("chapter_loop", chapterLoop);
        return detail;
    }

    public void importTemplateDocuments(String projectId) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:" + TEMPLATE_PREFIX + "**/*");
        for (Resource resource : resources) {
            if (!resource.isReadable() || resource.getURL().toString().endsWith("/")) {
                continue;
            }
            String url = resource.getURL().toString();
            int idx = url.indexOf(TEMPLATE_PREFIX);
            if (idx < 0) {
                continue;
            }
            String relPath = url.substring(idx + TEMPLATE_PREFIX.length());
            if (relPath.isEmpty()) {
                continue;
            }
            byte[] bytes = org.springframework.util.StreamUtils.copyToByteArray(resource.getInputStream());
            documentService.writeFile(projectId, relPath, new String(bytes, StandardCharsets.UTF_8));
        }
    }

    @Transactional
    public void importFromFilesystem(String projectId, java.io.File projectDir) throws IOException {
        documentService.validateProjectId(projectId);
        if (projectMapper.selectById(projectId) != null) {
            return;
        }

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setTitle(projectId);
        novelConfigService.applyDefaults(project);
        project.setCurrentStage("idea");
        project.setCurrentChapter(0);
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.insert(project);

        importDirectory(projectDir, projectId, "");
        documentService.ensureProjectBootstrapFiles(projectId);
        promptRecordService.seedProjectTemplates(projectId);
        documentService.backfillMetadata(projectId);
        Map<String, Object> state = workflowStateService.loadState(projectId);
        project.setTitle(String.valueOf(state.getOrDefault("title", projectId)));
        if (state.get("current_stage") != null) {
            project.setCurrentStage(String.valueOf(state.get("current_stage")));
        }
        if (state.get("current_chapter") instanceof Number) {
            project.setCurrentChapter(((Number) state.get("current_chapter")).intValue());
        }
        if (state.get("target_chapters") instanceof Number) {
            project.setTargetChapters(((Number) state.get("target_chapters")).intValue());
        }
        try {
            project.setStateJson(objectMapper.writeValueAsString(state));
            project.setCompletedStages(objectMapper.writeValueAsString(
                    workflowStateService.getCompletedStages(state)));
        } catch (Exception ignored) {
        }
        projectMapper.updateById(project);
        novelConfigService.syncWorkflowStateForProject(projectId);
    }

    private void importDirectory(java.io.File dir, String projectId, String prefix) throws IOException {
        java.io.File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (java.io.File f : files) {
            String rel = prefix.isEmpty() ? f.getName() : prefix + "/" + f.getName();
            if (f.isDirectory()) {
                importDirectory(f, projectId, rel);
            } else if (rel.matches(".*\\.(md|json|yaml|yml)$")) {
                byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
                documentService.writeFile(projectId, rel.replace("\\", "/"), new String(bytes, StandardCharsets.UTF_8));
            }
        }
    }
}
