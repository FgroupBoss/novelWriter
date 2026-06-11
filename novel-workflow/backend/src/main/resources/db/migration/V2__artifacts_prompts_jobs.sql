ALTER TABLE nw_document
    ADD COLUMN artifact_type VARCHAR(32)  DEFAULT 'meta' COMMENT 'idea|setting|summary|chapter|review|material|plot|prompt|raw|meta',
    ADD COLUMN category     VARCHAR(64)  DEFAULT '其他' COMMENT 'UI 分组',
    ADD COLUMN stage_id     VARCHAR(64)  NULL,
    ADD COLUMN chapter      INT          NULL,
    ADD COLUMN source_type  VARCHAR(16)  DEFAULT 'manual' COMMENT 'llm|manual|template|input',
    ADD COLUMN job_id       VARCHAR(64)  NULL,
    ADD KEY idx_doc_category (project_id, category),
    ADD KEY idx_doc_artifact (project_id, artifact_type);

ALTER TABLE nw_job
    ADD COLUMN task_payload_json JSON NULL,
    ADD COLUMN progress_step       INT          DEFAULT 0,
    ADD COLUMN progress_total      INT          DEFAULT 0,
    ADD COLUMN progress_label      VARCHAR(255) DEFAULT '',
    ADD COLUMN paused_at           DATETIME     NULL;

CREATE TABLE IF NOT EXISTS nw_prompt_record (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id  VARCHAR(64)  NOT NULL,
    job_id      VARCHAR(64)  NULL,
    stage_id    VARCHAR(64)  NULL,
    chapter     INT          NULL,
    prompt_type VARCHAR(32)  NOT NULL COMMENT 'template|system|user|task',
    rel_path    VARCHAR(512) NULL,
    content     LONGTEXT     NOT NULL,
    char_count  INT          DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_prompt_project (project_id),
    KEY idx_prompt_job (job_id),
    KEY idx_prompt_stage (project_id, stage_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
