package com.novelwriter.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 根据相对路径推断产物类型与 UI 分类。
 */
public final class ArtifactClassifier {

    private ArtifactClassifier() {
    }

    public static class Meta {
        private final String artifactType;
        private final String category;

        public Meta(String artifactType, String category) {
            this.artifactType = artifactType;
            this.category = category;
        }

        public String getArtifactType() {
            return artifactType;
        }

        public String getCategory() {
            return category;
        }
    }

    public static Meta classify(String relPath) {
        String path = relPath.replace("\\", "/");
        if ("00_idea.md".equals(path)) {
            return new Meta("idea", "创意");
        }
        if (path.startsWith("meta/logs/") && path.endsWith("_raw.md")) {
            return new Meta("raw", "LLM 原始");
        }
        if (path.startsWith("meta/summaries/")) {
            return new Meta("summary", "摘要");
        }
        if (path.startsWith("09_chapters/") && path.endsWith("_review.md")) {
            return new Meta("review", "章节校验");
        }
        if (path.startsWith("09_chapters/") && path.endsWith("_summary.md")) {
            return new Meta("summary", "章节摘要");
        }
        if (path.startsWith("09_chapters/") && path.matches("09_chapters/ch\\d{3}\\.md")) {
            return new Meta("chapter", "正文");
        }
        if ("08_material_library.md".equals(path)) {
            return new Meta("material", "素材库");
        }
        if ("10_plot_progress.md".equals(path) || "07_foreshadowing.md".equals(path) || "12_timeline.md".equals(path)) {
            return new Meta("plot", "剧情推进");
        }
        if (path.matches("0[1-6]_.*\\.md") || "11_style_guide.md".equals(path)
                || "13_themes_symbols.md".equals(path) || "14_pacing_notes.md".equals(path)) {
            return new Meta("setting", "设定");
        }
        if (path.startsWith("meta/")) {
            return new Meta("meta", "元数据");
        }
        return new Meta("meta", "其他");
    }

    public static Map<String, String> categoryLabels() {
        Map<String, String> labels = new LinkedHashMap<String, String>();
        labels.put("创意", "创意");
        labels.put("设定", "设定");
        labels.put("摘要", "摘要");
        labels.put("正文", "正文");
        labels.put("章节校验", "章节校验");
        labels.put("素材库", "素材");
        labels.put("剧情推进", "剧情");
        labels.put("LLM 原始", "LLM 原始");
        labels.put("元数据", "元数据");
        labels.put("其他", "其他");
        return labels;
    }
}
