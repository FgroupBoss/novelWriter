CREATE TABLE IF NOT EXISTS nw_project (
    id              VARCHAR(64)  NOT NULL PRIMARY KEY,
    title           VARCHAR(255) NOT NULL DEFAULT '',
    current_stage   VARCHAR(64)  NOT NULL DEFAULT 'idea',
    current_chapter INT          NOT NULL DEFAULT 0,
    completed_stages JSON,
    state_json      JSON,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS nw_document (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id VARCHAR(64)  NOT NULL,
    rel_path   VARCHAR(512) NOT NULL,
    content    LONGTEXT     NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_path (project_id, rel_path),
    KEY idx_project (project_id),
    CONSTRAINT fk_doc_project FOREIGN KEY (project_id) REFERENCES nw_project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS nw_job (
    id          VARCHAR(64)  NOT NULL PRIMARY KEY,
    project_id  VARCHAR(64),
    job_type    VARCHAR(64),
    label       VARCHAR(255),
    status      VARCHAR(32)  NOT NULL DEFAULT 'running',
    logs        JSON,
    result_json JSON,
    error_msg   TEXT,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at DATETIME,
    KEY idx_job_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS nw_settings (
    id         INT          NOT NULL PRIMARY KEY DEFAULT 1,
    api_key    VARCHAR(512) DEFAULT '',
    base_url   VARCHAR(512) DEFAULT 'https://api.openai.com/v1',
    model      VARCHAR(128) DEFAULT 'gpt-4o',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO nw_settings (id, api_key, base_url, model) VALUES (1, '', 'https://api.openai.com/v1', 'gpt-4o');

CREATE TABLE IF NOT EXISTS nw_llm_log (
    id                 BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id         VARCHAR(64),
    stage              VARCHAR(64),
    chapter            INT,
    prompt_tokens      INT,
    completion_tokens  INT,
    total_tokens       INT,
    cache_hit_tokens   INT,
    cache_miss_tokens  INT,
    raw_response       LONGTEXT,
    created_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_llm_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
