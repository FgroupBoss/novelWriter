package com.novelwriter.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskPayload {
    private String kind;
    private String stage;
    private Integer chapter;
    private Integer fromChapter;
    private Integer toChapter;
    private Boolean setupOnly;
    private Boolean skipSetup;
    private String chapters;
    private Boolean dryRun;

    /**
     * 兼容 Map 入参（snake_case）与 Jackson 序列化（camelCase）。
     */
    public static TaskPayload fromMap(Map<String, Object> map) {
        if (map == null) {
            return TaskPayload.builder().build();
        }
        TaskPayload.TaskPayloadBuilder b = TaskPayload.builder();
        String kind = str(map, "kind");
        if (kind != null) {
            b.kind(kind);
        }
        String stage = str(map, "stage");
        if (stage != null) {
            b.stage(stage);
        }
        Integer chapter = num(map, "chapter");
        if (chapter != null) {
            b.chapter(chapter);
        }
        Integer fromChapter = num(map, "from_chapter", "fromChapter");
        if (fromChapter != null) {
            b.fromChapter(fromChapter);
        }
        Integer toChapter = num(map, "to_chapter", "toChapter");
        if (toChapter != null) {
            b.toChapter(toChapter);
        }
        Boolean setupOnly = bool(map, "setup_only", "setupOnly");
        if (setupOnly != null) {
            b.setupOnly(setupOnly);
        }
        Boolean skipSetup = bool(map, "skip_setup", "skipSetup");
        if (skipSetup != null) {
            b.skipSetup(skipSetup);
        }
        String chapters = str(map, "chapters");
        if (chapters != null) {
            b.chapters(chapters);
        }
        Boolean dryRun = bool(map, "dry_run", "dryRun");
        if (dryRun != null) {
            b.dryRun(dryRun);
        }
        return b.build();
    }

    private static String str(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.get(key) != null) {
                return String.valueOf(map.get(key));
            }
        }
        return null;
    }

    private static Integer num(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val instanceof Number) {
                return ((Number) val).intValue();
            }
        }
        return null;
    }

    private static Boolean bool(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val != null) {
                return Boolean.TRUE.equals(val);
            }
        }
        return null;
    }
}
