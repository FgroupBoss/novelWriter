-- 小说维度配置（原 config.yaml novel + context + project.language）
ALTER TABLE nw_project
    ADD COLUMN language                   VARCHAR(16)  NOT NULL DEFAULT 'zh-CN' AFTER title,
    ADD COLUMN scale                      VARCHAR(32)  NOT NULL DEFAULT 'long' AFTER language,
    ADD COLUMN target_chapters            INT          NOT NULL DEFAULT 80 AFTER scale,
    ADD COLUMN chapters_per_volume        INT          NOT NULL DEFAULT 20 AFTER target_chapters,
    ADD COLUMN words_per_chapter          INT          NOT NULL DEFAULT 3000 AFTER chapters_per_volume,
    ADD COLUMN summary_max_chars          INT          NOT NULL DEFAULT 400 AFTER words_per_chapter,
    ADD COLUMN plot_progress_max_chars    INT          NOT NULL DEFAULT 2000 AFTER summary_max_chars,
    ADD COLUMN prev_chapter_summary_chars INT          NOT NULL DEFAULT 600 AFTER plot_progress_max_chars;
