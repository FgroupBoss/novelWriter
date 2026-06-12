package com.novelwriter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 加载 stages.yaml / config.yaml。
 */
@Slf4j
@Component
@Getter
public class StageConfigService {

    private Map<String, Object> stagesConfig = Collections.emptyMap();
    private Map<String, Object> appConfig = Collections.emptyMap();

    @Value("${novel.default-target-chapters:80}")
    private int defaultTargetChapters;

    @PostConstruct
    public void init() throws IOException {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        try (InputStream stages = new ClassPathResource("stages.yaml").getInputStream()) {
            stagesConfig = yamlMapper.readValue(stages, Map.class);
        }
        try (InputStream config = new ClassPathResource("config.yaml").getInputStream()) {
            appConfig = yamlMapper.readValue(config, Map.class);
        }
        log.info("阶段配置已加载 setup_order={}", getSetupOrder());
    }

    @SuppressWarnings("unchecked")
    public List<String> getSetupOrder() {
        Object order = stagesConfig.get("setup_order");
        return order instanceof List ? (List<String>) order : Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public List<String> getChapterLoop() {
        Object loop = stagesConfig.get("chapter_loop");
        return loop instanceof List ? (List<String>) loop : Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getSetupStage(String stageId) {
        Map<String, Object> stages = (Map<String, Object>) stagesConfig.get("stages");
        if (stages == null) {
            return null;
        }
        Object spec = stages.get(stageId);
        return spec instanceof Map ? (Map<String, Object>) spec : null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getChapterStage(String stageId) {
        Map<String, Object> stages = (Map<String, Object>) stagesConfig.get("chapter_stages");
        if (stages == null) {
            return null;
        }
        Object spec = stages.get(stageId);
        return spec instanceof Map ? (Map<String, Object>) spec : null;
    }

    public boolean isChapterStage(String stageId) {
        return getChapterStage(stageId) != null;
    }

    public int getTargetChapters() {
        return getDefaultTargetChapters();
    }

    public int getDefaultTargetChapters() {
        Object novel = appConfig.get("novel");
        if (novel instanceof Map) {
            Object tc = ((Map<?, ?>) novel).get("target_chapters");
            if (tc instanceof Number) {
                return ((Number) tc).intValue();
            }
        }
        return defaultTargetChapters;
    }

    /**
     * 新建项目时的默认小说参数（config.yaml 中 project / novel / context）。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDefaultNovelConfig() {
        Map<String, Object> defaults = new LinkedHashMap<String, Object>();
        Object project = appConfig.get("project");
        if (project instanceof Map) {
            Object lang = ((Map<?, ?>) project).get("language");
            if (lang != null) {
                defaults.put("language", String.valueOf(lang));
            }
        }
        Object novel = appConfig.get("novel");
        if (novel instanceof Map) {
            Map<String, Object> novelMap = (Map<String, Object>) novel;
            defaults.put("scale", novelMap.getOrDefault("scale", "long"));
            defaults.put("target_chapters", novelMap.getOrDefault("target_chapters", defaultTargetChapters));
            defaults.put("chapters_per_volume", novelMap.getOrDefault("chapters_per_volume", 20));
            defaults.put("words_per_chapter", novelMap.getOrDefault("words_per_chapter", 3000));
        }
        Object context = appConfig.get("context");
        if (context instanceof Map) {
            Map<String, Object> ctxMap = (Map<String, Object>) context;
            defaults.put("summary_max_chars", ctxMap.getOrDefault("summary_max_chars", 400));
            defaults.put("plot_progress_max_chars", ctxMap.getOrDefault("plot_progress_max_chars", 2000));
            defaults.put("prev_chapter_summary_chars", ctxMap.getOrDefault("prev_chapter_summary_chars", 600));
        }
        defaults.putIfAbsent("language", "zh-CN");
        defaults.putIfAbsent("scale", "long");
        defaults.putIfAbsent("target_chapters", defaultTargetChapters);
        defaults.putIfAbsent("chapters_per_volume", 20);
        defaults.putIfAbsent("words_per_chapter", 3000);
        defaults.putIfAbsent("summary_max_chars", 400);
        defaults.putIfAbsent("plot_progress_max_chars", 2000);
        defaults.putIfAbsent("prev_chapter_summary_chars", 600);
        return defaults;
    }

    public static final Map<String, String> STAGE_LABELS = buildStageLabels();

    private static Map<String, String> buildStageLabels() {
        Map<String, String> labels = new LinkedHashMap<String, String>();
        labels.put("era_setting", "时代设定");
        labels.put("characters", "角色设定");
        labels.put("worldview", "世界观");
        labels.put("relationships", "关系网");
        labels.put("main_outline", "主线大纲");
        labels.put("sub_outline", "支线大纲");
        labels.put("foreshadowing", "伏笔与回收");
        labels.put("material_library", "素材库");
        labels.put("style_guide", "文风指南");
        labels.put("timeline", "时间线");
        labels.put("themes_symbols", "主题象征");
        labels.put("pacing_notes", "节奏说明");
        labels.put("context_index", "上下文索引");
        labels.put("chapter_write", "撰写正文");
        labels.put("plot_update", "更新剧情");
        labels.put("chapter_review", "章节校验");
        return labels;
    }
}
