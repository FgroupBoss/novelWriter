package com.novelwriter.service;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
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

    public static TaskPayload fromMap(Map<String, Object> map) {
        if (map == null) {
            return TaskPayload.builder().build();
        }
        TaskPayload.TaskPayloadBuilder b = TaskPayload.builder();
        if (map.get("kind") != null) {
            b.kind(String.valueOf(map.get("kind")));
        }
        if (map.get("stage") != null) {
            b.stage(String.valueOf(map.get("stage")));
        }
        if (map.get("chapter") != null) {
            b.chapter(((Number) map.get("chapter")).intValue());
        }
        if (map.get("from_chapter") != null) {
            b.fromChapter(((Number) map.get("from_chapter")).intValue());
        }
        if (map.get("to_chapter") != null) {
            b.toChapter(((Number) map.get("to_chapter")).intValue());
        }
        if (map.get("setup_only") != null) {
            b.setupOnly(Boolean.TRUE.equals(map.get("setup_only")));
        }
        if (map.get("skip_setup") != null) {
            b.skipSetup(Boolean.TRUE.equals(map.get("skip_setup")));
        }
        if (map.get("chapters") != null) {
            b.chapters(String.valueOf(map.get("chapters")));
        }
        if (map.get("dry_run") != null) {
            b.dryRun(Boolean.TRUE.equals(map.get("dry_run")));
        }
        return b.build();
    }
}
