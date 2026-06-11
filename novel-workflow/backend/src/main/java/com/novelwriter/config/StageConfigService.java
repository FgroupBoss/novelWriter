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
 * 加载 stages.yaml / config.yaml，与 Python config.py 对齐。
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
        Object novel = appConfig.get("novel");
        if (novel instanceof Map) {
            Object tc = ((Map<?, ?>) novel).get("target_chapters");
            if (tc instanceof Number) {
                return ((Number) tc).intValue();
            }
        }
        return defaultTargetChapters;
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
