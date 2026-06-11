package com.novelwriter.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novelwriter.config.StageConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 试运行：不调用 API，检查各阶段上下文就绪情况与 Token 体量。
 */
@Service
@RequiredArgsConstructor
public class DryRunService {

    private final StageConfigService stageConfigService;
    private final ContextAssemblyService contextAssemblyService;
    private final DocumentService documentService;
    private final WorkflowStateService workflowStateService;
    private final ObjectMapper objectMapper;

    public Map<String, Object> buildDryRunReport(String projectId) {
        documentService.ensureProjectBootstrapFiles(projectId);
        boolean ideaReady = true;
        String ideaMessage = "创意已填写";
        try {
            workflowStateService.ensureIdeaFilled(projectId);
        } catch (Exception e) {
            ideaReady = false;
            ideaMessage = e.getMessage();
        }

        List<Map<String, Object>> stageResults = new ArrayList<Map<String, Object>>();
        int runnable = 0;
        int totalCharsSum = 0;

        for (StageItem item : buildStageList()) {
            String label = StageConfigService.STAGE_LABELS.getOrDefault(item.stageId, item.stageId);
            if (item.chapter != null) {
                label = "第" + item.chapter + "章 · " + label;
            }

            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            entry.put("stage", item.stageId);
            entry.put("label", label);
            entry.put("chapter", item.chapter);
            entry.put("ready", false);

            try {
                List<String> ctxFiles = contextAssemblyService.resolveContextFiles(
                        item.stageId, item.chapter, item.chapterStage, true);
                List<String> outFiles = contextAssemblyService.resolveOutputFiles(
                        item.stageId, item.chapter, item.chapterStage);
                String userMsg = contextAssemblyService.buildUserMessage(
                        projectId, item.stageId, outFiles, item.chapter);

                entry.put("ready", true);
                entry.put("context_files", ctxFiles);
                entry.put("output_files", outFiles);
                entry.put("context_chars", estimateContextChars(projectId, ctxFiles));
                entry.put("total_chars", userMsg.length());
                runnable++;
                totalCharsSum += userMsg.length();
            } catch (Exception e) {
                List<String> blocked = parseMissingFiles(e.getMessage());
                if (!blocked.isEmpty()) {
                    entry.put("blocked_by", blocked);
                }
                entry.put("error", e.getMessage());
            }
            stageResults.add(entry);
        }

        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("runnable_stages", runnable);
        summary.put("total_stages", stageResults.size());
        summary.put("runnable_prompt_chars", totalCharsSum);
        summary.put("note", "仅统计当前可运行阶段的 prompt 字符；前置未生成时后续阶段会 blocked。");

        Map<String, Object> report = new LinkedHashMap<String, Object>();
        report.put("project_id", projectId);
        report.put("idea_ready", ideaReady);
        report.put("idea_message", ideaMessage);
        report.put("stages", stageResults);
        report.put("summary", summary);

        saveReport(projectId, report);
        return report;
    }

    public Map<String, Object> buildContextPreview(String projectId, String stage, Integer chapter) throws Exception {
        boolean chapterStage = stageConfigService.isChapterStage(stage);
        if (stageConfigService.getSetupStage(stage) == null && !chapterStage) {
            throw new IllegalArgumentException("未知阶段: " + stage);
        }

        List<String> ctxFiles = contextAssemblyService.resolveContextFiles(stage, chapter, chapterStage, true);
        List<String> outFiles = contextAssemblyService.resolveOutputFiles(stage, chapter, chapterStage);
        Set<String> allowMissing = chapterStage && chapter != null
                ? chapterAllowMissing(stage, chapter) : java.util.Collections.<String>emptySet();

        List<Map<String, Object>> fileDetails = new ArrayList<Map<String, Object>>();
        for (String rel : ctxFiles) {
            Map<String, Object> detail = analyzeContextFile(projectId, rel);
            if (!Boolean.TRUE.equals(detail.get("ready")) && allowMissing.contains(rel)) {
                detail.put("ready", true);
                detail.put("status", "skip");
                detail.put("chars", ContextAssemblyService.CONTEXT_SKIP_MARKER.length());
                detail.put("preview", ContextAssemblyService.CONTEXT_SKIP_MARKER);
            }
            fileDetails.add(detail);
        }

        List<String> missingFiles = new ArrayList<String>();
        int contextChars = 0;
        for (Map<String, Object> f : fileDetails) {
            if (!Boolean.TRUE.equals(f.get("ready"))) {
                missingFiles.add(String.valueOf(f.get("path")));
            } else {
                contextChars += ((Number) f.get("chars")).intValue();
            }
        }

        int promptOverhead = loadTaskPromptChars(stage, chapter);
        List<Map<String, Object>> outputDetails = new ArrayList<Map<String, Object>>();
        for (String rel : outFiles) {
            outputDetails.add(analyzeContextFile(projectId, rel));
        }

        Map<String, Object> preview = new LinkedHashMap<String, Object>();
        preview.put("stage", stage);
        preview.put("chapter", chapter);
        preview.put("label", StageConfigService.STAGE_LABELS.getOrDefault(stage, stage));
        preview.put("context_files", ctxFiles);
        preview.put("output_files", outFiles);
        preview.put("file_details", fileDetails);
        preview.put("output_details", outputDetails);
        preview.put("missing_files", missingFiles);
        preview.put("context_chars", contextChars);
        preview.put("prompt_overhead_chars", promptOverhead);
        preview.put("total_chars", contextChars + promptOverhead);
        preview.put("ready", missingFiles.isEmpty());
        return preview;
    }

    private List<StageItem> buildStageList() {
        List<StageItem> items = new ArrayList<StageItem>();
        for (String sid : stageConfigService.getSetupOrder()) {
            items.add(new StageItem(sid, null, false));
        }
        for (String sid : stageConfigService.getChapterLoop()) {
            items.add(new StageItem(sid, 1, true));
        }
        return items;
    }

    private int estimateContextChars(String projectId, List<String> ctxFiles) {
        int total = 0;
        for (String rel : ctxFiles) {
            String content = documentService.readContent(projectId, rel);
            if (content != null) {
                total += content.length();
            }
        }
        return total;
    }

    private List<String> parseMissingFiles(String errorMsg) {
        List<String> result = new ArrayList<String>();
        if (errorMsg == null) {
            return result;
        }
        for (String line : errorMsg.split("\n")) {
            line = line.trim();
            if (line.startsWith("- ")) {
                result.add(line.substring(2).trim());
            }
        }
        return result;
    }

    private void saveReport(String projectId, Map<String, Object> report) {
        try {
            documentService.writeFile(projectId, "meta/dry_run_report.json",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        } catch (Exception ignored) {
            // 非关键路径
        }
    }

    private Map<String, Object> analyzeContextFile(String projectId, String rel) {
        Map<String, Object> detail = new LinkedHashMap<String, Object>();
        detail.put("path", rel);
        String raw = documentService.readContent(projectId, rel);
        if (raw == null) {
            detail.put("exists", false);
            detail.put("ready", false);
            detail.put("chars", 0);
            detail.put("status", "missing");
            return detail;
        }
        String text = raw.trim();
        if (text.isEmpty() || documentService.isPlaceholderContent(text)) {
            detail.put("exists", true);
            detail.put("ready", false);
            detail.put("chars", 0);
            detail.put("status", "placeholder");
            detail.put("preview", text.length() > 100 ? text.substring(0, 100) : text);
            return detail;
        }
        detail.put("exists", true);
        detail.put("ready", true);
        detail.put("chars", raw.length());
        detail.put("status", "ok");
        detail.put("preview", text.length() > 150 ? text.substring(0, 150) : text);
        return detail;
    }

    private int loadTaskPromptChars(String stage, Integer chapter) {
        int overhead = 1032;
        try {
            String text = contextAssemblyService.loadTaskPrompt(stage, chapter);
            return overhead + text.length() + 400;
        } catch (Exception e) {
            return overhead + 2500;
        }
    }

    private Set<String> chapterAllowMissing(String stage, int chapter) {
        java.util.Set<String> result = new java.util.HashSet<String>();
        if ("chapter_write".equals(stage)) {
            result.add(String.format("09_chapters/ch%03d.md", chapter));
            result.add(String.format("09_chapters/ch%03d_summary.md", chapter));
        }
        return result;
    }

    private static class StageItem {
        final String stageId;
        final Integer chapter;
        final boolean chapterStage;

        StageItem(String stageId, Integer chapter, boolean chapterStage) {
            this.stageId = stageId;
            this.chapter = chapter;
            this.chapterStage = chapterStage;
        }
    }
}
