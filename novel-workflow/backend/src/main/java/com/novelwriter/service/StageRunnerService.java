package com.novelwriter.service;

import com.novelwriter.common.AssistException;
import com.novelwriter.config.StageConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StageRunnerService {

    private final StageConfigService stageConfigService;
    private final ContextAssemblyService contextAssemblyService;
    private final FileBlockParserService fileBlockParserService;
    private final LlmClientService llmClientService;
    private final DocumentService documentService;
    private final WorkflowStateService workflowStateService;
    private final PromptRecordService promptRecordService;

    public Map<String, Object> runSetupStage(String projectId, String stageName, boolean dryRun, JobContext ctx) {
        if (stageConfigService.getSetupStage(stageName) == null) {
            throw new AssistException("未知设定阶段: " + stageName);
        }
        if ("era_setting".equals(stageName)) {
            workflowStateService.ensureIdeaFilled(projectId);
        }
        if ("context_index".equals(stageName)) {
            documentService.ensureProjectBootstrapFiles(projectId);
        }
        List<String> outputFiles = contextAssemblyService.resolveOutputFiles(stageName, null, false);
        return execute(projectId, stageName, outputFiles, null, false, dryRun, ctx);
    }

    public Map<String, Object> runChapterStage(String projectId, String stageName, int chapter, boolean dryRun, JobContext ctx) {
        if (stageConfigService.getChapterStage(stageName) == null) {
            throw new AssistException("未知章节阶段: " + stageName);
        }
        if (chapter < 1) {
            throw new AssistException("章节号须 >= 1");
        }
        List<String> outputFiles = contextAssemblyService.resolveOutputFiles(stageName, chapter, true);
        return execute(projectId, stageName, outputFiles, chapter, true, dryRun, ctx);
    }

    private Map<String, Object> execute(String projectId, String stageName, List<String> outputFiles,
                                          Integer chapter, boolean chapterStage, boolean dryRun, JobContext ctx) {
        try {
            String system = contextAssemblyService.loadSystemPrompt();
            String user = contextAssemblyService.buildUserMessage(projectId, stageName, outputFiles, chapter);
            String jobId = ctx != null ? ctx.getJobId() : null;

            if (ctx != null) {
                ctx.log("组装 Prompt 完成，user 消息 " + user.length() + " 字符");
            }

            saveTaskPrompt(projectId, jobId, stageName, chapter);
            if (!dryRun) {
                promptRecordService.save(projectId, jobId, stageName, chapter, "system", null, system);
                promptRecordService.save(projectId, jobId, stageName, chapter, "user", null, user);
            }

            if (dryRun) {
                log.info("[dry-run] 跳过 LLM 调用 stage={} chapter={}", stageName, chapter);
                Map<String, Object> result = new LinkedHashMap<String, Object>();
                result.put("stage", stageName);
                result.put("chapter", chapter);
                result.put("dry_run", true);
                result.put("total_chars", user.length());
                result.put("output_files", outputFiles);
                return result;
            }

            if (ctx != null) {
                ctx.log("调用 LLM stage=" + stageName + (chapter != null ? " chapter=" + chapter : ""));
            }

            LlmClientService.LlmChatResult chatResult = llmClientService.chat(
                    system, user, projectId, stageName, chapter);
            saveRawResponse(projectId, stageName, chapter, chatResult.getContent(), jobId);

            if (ctx != null && chatResult.getCacheHitTokens() != null) {
                ctx.log("Token: prompt=" + chatResult.getPromptTokens()
                        + " cache_hit=" + chatResult.getCacheHitTokens()
                        + " cache_miss=" + chatResult.getCacheMissTokens());
            }

            Map<String, String> parsed = fileBlockParserService.parseFileBlocks(chatResult.getContent(), outputFiles);
            FileBlockParserService.ParseResult validated = fileBlockParserService.validateOutputs(outputFiles, parsed);
            if (!validated.getMissing().isEmpty()) {
                throw new AssistException("模型输出缺少文件: " + validated.getMissing() + "。原始回复已保存，可重试。");
            }

            List<String> written = writeFiles(projectId, validated.getValidated(), stageName, chapter, jobId);
            if (!chapterStage) {
                workflowStateService.markStageComplete(projectId, stageName, null);
            } else if ("chapter_review".equals(stageName) && chapter != null) {
                workflowStateService.markStageComplete(projectId, stageName, chapter);
            }

            if (ctx != null) {
                ctx.log("阶段完成，写入 " + written.size() + " 个产物");
            }
            log.info("阶段完成 stage={} 写入 {} 个文件", stageName, written.size());
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("stage", stageName);
            result.put("chapter", chapter);
            result.put("files", written);
            return result;
        } catch (AssistException e) {
            throw e;
        } catch (Exception e) {
            throw new AssistException("阶段执行失败: " + e.getMessage(), e);
        }
    }

    private void saveTaskPrompt(String projectId, String jobId, String stageName, Integer chapter) {
        try {
            String task = contextAssemblyService.loadTaskPrompt(stageName, chapter);
            String promptFile = resolvePromptFile(stageName);
            promptRecordService.save(projectId, jobId, stageName, chapter, "task", promptFile, task);
        } catch (Exception e) {
            log.warn("保存 task prompt 失败 stage={}", stageName, e);
        }
    }

    private String resolvePromptFile(String stageName) {
        if (stageConfigService.isChapterStage(stageName)) {
            Map<String, Object> spec = stageConfigService.getChapterStage(stageName);
            return spec != null ? String.valueOf(spec.get("prompt")) : stageName;
        }
        Map<String, Object> spec = stageConfigService.getSetupStage(stageName);
        return spec != null ? String.valueOf(spec.get("prompt")) : stageName;
    }

    private List<String> writeFiles(String projectId, Map<String, String> files,
                                    String stageName, Integer chapter, String jobId) {
        List<String> written = new ArrayList<String>();
        DocumentWriteMeta meta = DocumentWriteMeta.builder()
                .stageId(stageName)
                .chapter(chapter)
                .sourceType("llm")
                .jobId(jobId)
                .build();
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String content = entry.getValue().trim() + "\n";
            documentService.writeFile(projectId, entry.getKey(), content, meta);
            written.add(entry.getKey());
            log.info("已写入: {} ({} 字符)", entry.getKey(), entry.getValue().length());
        }
        return written;
    }

    private void saveRawResponse(String projectId, String stageName, Integer chapter, String raw, String jobId) {
        String suffix = chapter != null ? "_ch" + String.format("%03d", chapter) : "";
        String path = "meta/logs/" + stageName + suffix + "_raw.md";
        DocumentWriteMeta meta = DocumentWriteMeta.builder()
                .stageId(stageName)
                .chapter(chapter)
                .sourceType("llm")
                .jobId(jobId)
                .build();
        documentService.writeFile(projectId, path, raw, meta);
        log.info("原始回复已保存: {}", path);
    }
}
