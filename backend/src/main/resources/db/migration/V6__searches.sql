-- Phase 3: saved searches, executions (per search and per source), new-job detection, notifications.

CREATE TABLE saved_searches (
    id                      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id                 BIGINT UNSIGNED NOT NULL,
    name                    VARCHAR(120)    NOT NULL,
    profile_id              BIGINT UNSIGNED NULL,
    keywords                VARCHAR(300)    NULL,
    filters                 JSON            NOT NULL,
    source_codes            JSON            NOT NULL,
    schedule_type           VARCHAR(16)     NOT NULL DEFAULT 'MANUAL',
    interval_minutes        INT             NULL,
    daily_time              TIME            NULL,
    timezone                VARCHAR(64)     NOT NULL DEFAULT 'America/La_Paz',
    enabled                 BOOLEAN         NOT NULL DEFAULT TRUE,
    next_run_at             DATETIME(6)     NULL,
    last_run_at             DATETIME(6)     NULL,
    max_results_per_source  INT             NOT NULL DEFAULT 50,
    notify_on_new           BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at              DATETIME(6)     NULL,
    version                 INT             NOT NULL DEFAULT 0,
    CONSTRAINT fk_ss_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_ss_schedule CHECK (schedule_type IN ('MANUAL', 'INTERVAL', 'DAILY')),
    CONSTRAINT ck_ss_interval CHECK (interval_minutes IS NULL OR interval_minutes >= 60),
    INDEX ix_ss_user (user_id, deleted_at),
    INDEX ix_ss_due (enabled, next_run_at)
) ENGINE = InnoDB;

CREATE TABLE search_executions (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    saved_search_id  BIGINT UNSIGNED NOT NULL,
    user_id          BIGINT UNSIGNED NOT NULL,
    run_trigger      VARCHAR(16)     NOT NULL,
    status           VARCHAR(16)     NOT NULL,
    started_at       DATETIME(6)     NOT NULL,
    finished_at      DATETIME(6)     NULL,
    jobs_found       INT             NOT NULL DEFAULT 0,
    jobs_new         INT             NOT NULL DEFAULT 0,
    jobs_duplicate   INT             NOT NULL DEFAULT 0,
    cost_usd         DECIMAL(12, 6)  NOT NULL DEFAULT 0,
    error_summary    VARCHAR(1000)   NULL,
    CONSTRAINT fk_se_search FOREIGN KEY (saved_search_id) REFERENCES saved_searches (id) ON DELETE CASCADE,
    CONSTRAINT fk_se_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_se_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED')),
    INDEX ix_se_search (saved_search_id, started_at),
    INDEX ix_se_user (user_id, started_at)
) ENGINE = InnoDB;

CREATE TABLE search_execution_jobs (
    search_execution_id  BIGINT UNSIGNED NOT NULL,
    job_offer_id         BIGINT UNSIGNED NOT NULL,
    is_new               BOOLEAN         NOT NULL,
    PRIMARY KEY (search_execution_id, job_offer_id),
    CONSTRAINT fk_sej_exec FOREIGN KEY (search_execution_id) REFERENCES search_executions (id) ON DELETE CASCADE,
    CONSTRAINT fk_sej_offer FOREIGN KEY (job_offer_id) REFERENCES job_offers (id) ON DELETE CASCADE,
    INDEX ix_sej_offer (job_offer_id)
) ENGINE = InnoDB;

-- Which jobs each saved search has already reported (for "new opportunity" detection across runs).
CREATE TABLE saved_search_seen_jobs (
    saved_search_id  BIGINT UNSIGNED NOT NULL,
    job_offer_id     BIGINT UNSIGNED NOT NULL,
    first_seen_at    DATETIME(6)     NOT NULL,
    PRIMARY KEY (saved_search_id, job_offer_id),
    CONSTRAINT fk_sssj_search FOREIGN KEY (saved_search_id) REFERENCES saved_searches (id) ON DELETE CASCADE,
    CONSTRAINT fk_sssj_offer FOREIGN KEY (job_offer_id) REFERENCES job_offers (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE job_source_executions (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    job_source_id         BIGINT UNSIGNED NOT NULL,
    search_execution_id   BIGINT UNSIGNED NULL,
    user_id               BIGINT UNSIGNED NULL,
    status                VARCHAR(16)     NOT NULL,
    external_run_id       VARCHAR(64)     NULL,
    external_dataset_id   VARCHAR(64)     NULL,
    request_count         INT             NOT NULL DEFAULT 0,
    items_fetched         INT             NOT NULL DEFAULT 0,
    items_new             INT             NOT NULL DEFAULT 0,
    items_duplicate       INT             NOT NULL DEFAULT 0,
    cost_usd              DECIMAL(12, 6)  NOT NULL DEFAULT 0,
    started_at            DATETIME(6)     NOT NULL,
    finished_at           DATETIME(6)     NULL,
    error_code            VARCHAR(64)     NULL,
    error_message         VARCHAR(1000)   NULL,
    CONSTRAINT fk_jse_source FOREIGN KEY (job_source_id) REFERENCES job_sources (id),
    CONSTRAINT fk_jse_exec FOREIGN KEY (search_execution_id) REFERENCES search_executions (id) ON DELETE CASCADE,
    CONSTRAINT ck_jse_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'TIMED_OUT', 'ABORTED',
                                               'SKIPPED_BUDGET', 'SKIPPED_DISABLED', 'SKIPPED_INTERVAL', 'CACHED')),
    INDEX ix_jse_source_time (job_source_id, started_at),
    INDEX ix_jse_exec (search_execution_id),
    INDEX ix_jse_run (external_run_id)
) ENGINE = InnoDB;

CREATE TABLE notifications (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT UNSIGNED NOT NULL,
    type        VARCHAR(40)     NOT NULL,
    title       VARCHAR(200)    NOT NULL,
    body        VARCHAR(1000)   NULL,
    link        VARCHAR(300)    NULL,
    payload     JSON            NULL,
    read_at     DATETIME(6)     NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX ix_notif_user (user_id, read_at, created_at)
) ENGINE = InnoDB;
