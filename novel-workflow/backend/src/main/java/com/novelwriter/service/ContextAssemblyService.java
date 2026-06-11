package com.novelwriter.service;

import com.novelwriter.common.AssistException;
import com.novelwriter.config.StageConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按阶段组装最小上下文（DeepSeek 等前缀缓存友好）。
 * 与 Python context.py 1:1 对齐，确保 prefix cache 命中率。
 */
@Service
@RequiredArgsConstructor
public class ContextAssemblyService {

    public static final String CONTEXT_SKIP_MARKER = "[本文件本阶段未加载]";

    /** 设定阶段：上下文按此顺序排列，使相邻阶段共享最长前缀 */
    private static final List<String> SETUP_CONTEXT_ORDER = java.util.Arrays.asList(
            "00_idea.md",
            "01_era_setting.md",
            "02_characters.md",
            "03_worldview.md",
            "04_relationships.md",
            "05_main_outline.md",
            "06_sub_outline.md",
            "07_foreshadowing.md",
            "08_material_library.md",
            "meta/summaries/era_summary.md",
            "meta/summaries/characters_summary.md",
            "meta/summaries/worldview_summary.md",
            "meta/summaries/relationships_summary.md",
            "meta/summaries/main_outline_summary.md",
            "11_style_guide.md",
            "12_timeline.md",
            "13_themes_symbols.md",
            "14_pacing_notes.md",
            "meta/context_index.md",
            "meta/workflow_state.json"
    );

    /** 正文阶段：核心上下文（各章内 write → plot_update → review 共享此前缀） */
    private static final List<String> CHAPTER_CONTEXT_CORE = java.util.Arrays.asList(
            "11_style_guide.md",
            "meta/summaries/era_summary.md",
            "meta/summaries/characters_summary.md",
            "meta/summaries/worldview_summary.md",
            "meta/summaries/relationships_summary.md",
            "meta/summaries/main_outline_summary.md",
            "05_main_outline.md",
            "06_sub_outline.md",
            "08_material_library.md",
            "13_themes_symbols.md",
            "14_pacing_notes.md",
            "10_plot_progress.md",
            "07_foreshadowing.md",
            "12_timeline.md"
    );

    private final StageConfigService stageConfigService;
    private final DocumentService documentService;

    public List<String> resolveContextFiles(String stageName, Integer chapter, boolean chapterStage, boolean cacheFriendly) {
        if (chapterStage && cacheFriendly && chapter != null) {
            return chapterContextSlots(chapter);
        }

        Map<String, Object> spec = chapterStage
                ? stageConfigService.getChapterStage(stageName)
                : stageConfigService.getSetupStage(stageName);
        if (spec == null) {
            throw new AssistException("未知阶段: " + stageName);
        }

        List<String> files = new ArrayList<String>(getStringList(spec.get("context")));
        if (chapter != null && chapter > 1) {
            for (String tpl : getStringList(spec.get("context_if_chapter_gt_1"))) {
                files.add(formatPath(tpl, chapter));
            }
        }

        List<String> formatted = new ArrayList<String>();
        for (String f : files) {
            formatted.add(f.contains("{chapter") || f.contains("{prev") ? formatPath(f, chapter) : f);
        }

        if (chapterStage || !cacheFriendly) {
            return formatted;
        }
        return sortByOrder(formatted, SETUP_CONTEXT_ORDER);
    }

    public List<String> resolveOutputFiles(String stageName, Integer chapter, boolean chapterStage) {
        Map<String, Object> spec = chapterStage
                ? stageConfigService.getChapterStage(stageName)
                : stageConfigService.getSetupStage(stageName);
        if (spec == null) {
            throw new AssistException("未知阶段: " + stageName);
        }
        List<String> outputs = new ArrayList<String>();
        for (String f : getStringList(spec.get("outputs"))) {
            outputs.add(formatPath(f, chapter));
        }
        return outputs;
    }

    public String buildContextBlock(String projectId, List<String> relPaths, Set<String> allowMissing) {
        List<String> parts = new ArrayList<String>();
        List<String> missing = new ArrayList<String>();

        for (String rel : relPaths) {
            String content = documentService.readProjectFile(projectId, rel);
            if (content == null) {
                if (allowMissing != null && allowMissing.contains(rel)) {
                    parts.add("===CONTEXT_FILE: " + rel + "===\n" + CONTEXT_SKIP_MARKER + "\n===END_CONTEXT===");
                    continue;
                }
                missing.add(rel);
                continue;
            }
            parts.add("===CONTEXT_FILE: " + rel + "===\n" + content + "\n===END_CONTEXT===");
        }

        if (!missing.isEmpty()) {
            StringBuilder sb = new StringBuilder("以下上下文文件缺失或尚未生成，请先完成前置阶段:\n");
            for (String m : missing) {
                sb.append("  - ").append(m).append("\n");
            }
            throw new AssistException(sb.toString().trim());
        }
        return String.join("\n\n", parts);
    }

    public String loadSystemPrompt() throws IOException {
        String base = readPrompt("00_system_base.md");
        String outputRules = readPrompt("00_output_format_api.md");
        return base.trim() + "\n\n---\n\n" + outputRules.trim() + "\n";
    }

    public String loadTaskPrompt(String stageName, Integer chapter) throws IOException {
        Map<String, Object> spec;
        if (stageConfigService.isChapterStage(stageName)) {
            spec = stageConfigService.getChapterStage(stageName);
        } else {
            spec = stageConfigService.getSetupStage(stageName);
        }
        if (spec == null) {
            throw new AssistException("未知阶段: " + stageName);
        }
        String promptFile = String.valueOf(spec.get("prompt"));
        String text = readPrompt(promptFile);
        if (chapter != null) {
            text = text.replace("{N}", String.valueOf(chapter))
                    .replace("{NNN}", String.format("%03d", chapter));
        }
        return text;
    }

    /**
     * 用户消息结构（DeepSeek 前缀缓存优化）：
     * 1. 上下文块（固定顺序，动态文件置后）
     * 2. 当前任务（阶段指令、章号、输出清单 — 置后以便命中缓存）
     */
    public String buildUserMessage(String projectId, String stageName, List<String> outputFiles, Integer chapter)
            throws IOException {
        boolean chapterStage = stageConfigService.isChapterStage(stageName);
        List<String> contextFiles = resolveContextFiles(stageName, chapter, chapterStage, true);

        Set<String> allowMissing = new HashSet<String>();
        if (chapterStage && chapter != null) {
            allowMissing = chapterAllowMissing(stageName, chapter);
        }

        String contextBlock = buildContextBlock(projectId, contextFiles, allowMissing);
        String task = loadTaskPrompt(stageName, chapter);

        StringBuilder outputSpec = new StringBuilder();
        for (String p : outputFiles) {
            outputSpec.append("  - ").append(p).append("\n");
        }

        StringBuilder fileFormat = new StringBuilder();
        for (String p : outputFiles) {
            fileFormat.append("===FILE:").append(p).append("===\n")
                    .append("（此处写入 ").append(p).append(" 的完整 Markdown 内容）\n")
                    .append("===END===\n");
        }

        String chapterLine = chapter != null ? "chapter: " + chapter + "  (ch" + String.format("%03d", chapter) + ")\n" : "";

        return "## 已加载的上下文文件\n\n"
                + contextBlock + "\n\n"
                + "---\n\n"
                + "## 当前任务\n\n"
                + "> **落盘格式**：上文任务描述中的 markdown 代码块仅为**内容结构示例**；你必须使用下方 `===FILE:路径===` 块输出，不要用纯 Markdown 正文或 ``` 代码块代替。\n\n"
                + "stage_id: " + stageName + "\n"
                + chapterLine
                + task.trim() + "\n\n"
                + "### 本阶段输出文件（共 " + outputFiles.size() + " 个）\n\n"
                + outputSpec.toString().trim() + "\n\n"
                + "格式模板（逐块输出，不要省略标记）：\n\n"
                + fileFormat.toString().trim() + "\n";
    }

    private List<String> chapterContextSlots(int chapter) {
        List<String> slots = new ArrayList<String>(CHAPTER_CONTEXT_CORE);
        if (chapter > 1) {
            slots.add(formatPath("09_chapters/ch{prev:03d}_summary.md", chapter));
        }
        slots.add(formatPath("09_chapters/ch{chapter:03d}.md", chapter));
        slots.add(formatPath("09_chapters/ch{chapter:03d}_summary.md", chapter));
        return slots;
    }

    private Set<String> chapterAllowMissing(String stageName, int chapter) {
        String ch = formatPath("09_chapters/ch{chapter:03d}.md", chapter);
        String summary = formatPath("09_chapters/ch{chapter:03d}_summary.md", chapter);
        Set<String> result = new HashSet<String>();
        if ("chapter_write".equals(stageName)) {
            result.add(ch);
            result.add(summary);
        }
        return result;
    }

    private String formatPath(String template, Integer chapter) {
        if (chapter == null) {
            return template;
        }
        int prev = chapter - 1;
        return template
                .replace("{chapter:03d}", String.format("%03d", chapter))
                .replace("{prev:03d}", String.format("%03d", prev))
                .replace("{chapter}", String.valueOf(chapter))
                .replace("{prev}", String.valueOf(prev));
    }

    private List<String> sortByOrder(List<String> files, List<String> order) {
        final Map<String, Integer> rank = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < order.size(); i++) {
            rank.put(order.get(i), i);
        }

        List<String> unique = new ArrayList<String>(new LinkedHashSet<String>(files));
        Collections.sort(unique, new java.util.Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                int cmp = Integer.compare(rankKey(a, rank), rankKey(b, rank));
                return cmp != 0 ? cmp : a.compareTo(b);
            }
        });
        return unique;
    }

    private int rankKey(String path, Map<String, Integer> rank) {
        if (rank.containsKey(path)) {
            return rank.get(path);
        }
        if (path.startsWith("meta/summaries/")) {
            return 1000;
        }
        if (path.startsWith("09_chapters/")) {
            return 3000;
        }
        if (path.startsWith("meta/")) {
            return 2000;
        }
        return 1500;
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Object obj) {
        if (!(obj instanceof List)) {
            return Collections.emptyList();
        }
        return (List<String>) obj;
    }

    private String readPrompt(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("prompts/" + filename);
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
}
