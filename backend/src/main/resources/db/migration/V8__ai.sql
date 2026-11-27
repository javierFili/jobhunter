-- Phase 4: AI providers (DeepSeek + Claude), versioned prompts, per-task routing, analyses (cache), matches, requirements.

-- Prices: USD per 1M tokens, verified 2026-11-26 (DeepSeek: api-docs.deepseek.com/quick_start/pricing, peak prices;
-- off-peak = 50% outside 01-04 and 06-10 UTC Mon-Fri) and Anthropic's official model table (cached 2026-11-03).
-- They are editable configuration, never hard-coded. Haiku cache-read price unknown → input price used (conservative).
INSERT INTO integration_settings (integration_code, enabled, config, monthly_budget_usd) VALUES
('DEEPSEEK', FALSE, '{"models":["deepseek-flash","deepseek-v4-pro"],"offPeakDiscount":0.5,"peakWindowsUtc":["01:00-04:00","06:00-10:00"],"prices":{"deepseek-flash":{"input":0.30,"cachedInput":0.006,"output":1.20},"deepseek-v4-pro":{"input":1.32,"cachedInput":0.044,"output":3.96}}}', 10.00),
('CLAUDE', FALSE, '{"models":["claude-opus-5-5","claude-sonnet-5-5","claude-haiku-5-5"],"serverSideFallbacks":true,"prices":{"claude-opus-5-5":{"input":4.00,"cachedInput":0.20,"output":20.00},"claude-sonnet-5-5":{"input":2.00,"cachedInput":0.10,"output":10.00},"claude-haiku-5-5":{"input":0.10,"cachedInput":0.10,"output":0.50}}}', 10.00);

CREATE TABLE ai_prompt_versions (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    prompt_key         VARCHAR(32)     NOT NULL,
    version            INT             NOT NULL,
    system_template    TEXT            NOT NULL,
    user_template      TEXT            NOT NULL,
    output_schema      JSON            NOT NULL,
    temperature        DECIMAL(3, 2)   NULL,
    active             BOOLEAN         NOT NULL DEFAULT FALSE,
    checksum           CHAR(64)        NOT NULL,
    notes              VARCHAR(500)    NULL,
    created_by         BIGINT UNSIGNED NULL,
    created_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_prompt_version UNIQUE (prompt_key, version),
    INDEX ix_prompt_active (prompt_key, active)
) ENGINE = InnoDB;

CREATE TABLE ai_task_routes (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    prompt_key         VARCHAR(32)     NOT NULL,
    provider           VARCHAR(16)     NOT NULL,
    model              VARCHAR(64)     NOT NULL,
    effort             VARCHAR(8)      NULL,
    thinking           VARCHAR(8)      NULL,
    fallback_provider  VARCHAR(16)     NULL,
    fallback_model     VARCHAR(64)     NULL,
    max_output_tokens  INT             NOT NULL,
    updated_by         BIGINT UNSIGNED NULL,
    created_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version            INT             NOT NULL DEFAULT 0,
    CONSTRAINT uk_route_key UNIQUE (prompt_key),
    CONSTRAINT ck_route_provider CHECK (provider IN ('DEEPSEEK', 'CLAUDE')),
    CONSTRAINT ck_route_fallback CHECK (fallback_provider IS NULL OR fallback_provider IN ('DEEPSEEK', 'CLAUDE'))
) ENGINE = InnoDB;

-- Seeded with DeepSeek (user decision: both providers selectable per task from the panel).
INSERT INTO ai_task_routes (prompt_key, provider, model, effort, thinking, max_output_tokens) VALUES
('JOB_ANALYSIS',   'DEEPSEEK', 'deepseek-flash',  NULL, 'OFF', 4000),
('MATCH_SEMANTIC', 'DEEPSEEK', 'deepseek-flash',  NULL, 'OFF', 3000),
('CV_PARSE',       'DEEPSEEK', 'deepseek-flash',  NULL, 'OFF', 8000),
('CV_OPTIMIZE',    'DEEPSEEK', 'deepseek-v4-pro', NULL, 'ON',  16000),
('COVER_LETTER',   'DEEPSEEK', 'deepseek-flash',  NULL, 'OFF', 4000);

CREATE TABLE ai_analyses (
    id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id              BIGINT UNSIGNED NULL,
    prompt_version_id    BIGINT UNSIGNED NOT NULL,
    prompt_key           VARCHAR(32)     NOT NULL,
    provider             VARCHAR(16)     NOT NULL,
    model                VARCHAR(64)     NOT NULL,
    subject_type         VARCHAR(16)     NOT NULL,
    subject_id           BIGINT UNSIGNED NOT NULL,
    cache_key            CHAR(64)        NOT NULL,
    status               VARCHAR(16)     NOT NULL,
    output               JSON            NULL,
    input_tokens         INT             NULL,
    cached_input_tokens  INT             NULL,
    output_tokens        INT             NULL,
    cost_usd             DECIMAL(12, 6)  NOT NULL DEFAULT 0,
    latency_ms           INT             NULL,
    attempts             INT             NOT NULL DEFAULT 1,
    error                VARCHAR(1000)   NULL,
    created_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_ai_prompt FOREIGN KEY (prompt_version_id) REFERENCES ai_prompt_versions (id),
    CONSTRAINT fk_ai_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_ai_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'INVALID_OUTPUT', 'REFUSED')),
    INDEX ix_ai_cache (cache_key, status),
    INDEX ix_ai_subject (subject_type, subject_id, created_at),
    INDEX ix_ai_user (user_id, created_at)
) ENGINE = InnoDB;

CREATE TABLE job_requirements (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    job_offer_id    BIGINT UNSIGNED NOT NULL,
    kind            VARCHAR(16)     NOT NULL,
    importance      VARCHAR(12)     NOT NULL,
    skill_id        BIGINT UNSIGNED NULL,
    text            VARCHAR(500)    NOT NULL,
    min_years       DECIMAL(4, 1)   NULL,
    language_code   CHAR(2)         NULL,
    level           VARCHAR(8)      NULL,
    extracted_by    VARCHAR(16)     NOT NULL,
    ai_analysis_id  BIGINT UNSIGNED NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_jr_offer FOREIGN KEY (job_offer_id) REFERENCES job_offers (id) ON DELETE CASCADE,
    CONSTRAINT fk_jr_skill FOREIGN KEY (skill_id) REFERENCES skills (id),
    CONSTRAINT ck_jr_kind CHECK (kind IN ('SKILL', 'LANGUAGE', 'EXPERIENCE', 'EDUCATION', 'CERTIFICATION', 'OTHER')),
    CONSTRAINT ck_jr_importance CHECK (importance IN ('MANDATORY', 'PREFERRED')),
    INDEX ix_jr_offer (job_offer_id),
    INDEX ix_jr_skill (skill_id, importance)
) ENGINE = InnoDB;

CREATE TABLE job_matches (
    id                     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id                BIGINT UNSIGNED NOT NULL,
    profile_key            BIGINT UNSIGNED NOT NULL DEFAULT 0,
    job_offer_id           BIGINT UNSIGNED NOT NULL,
    profile_snapshot_hash  CHAR(64)        NOT NULL,
    job_content_hash       BINARY(32)      NOT NULL,
    score_total            TINYINT UNSIGNED NOT NULL,
    score_skills           TINYINT UNSIGNED NULL,
    score_experience       TINYINT UNSIGNED NULL,
    score_language         TINYINT UNSIGNED NULL,
    score_seniority        TINYINT UNSIGNED NULL,
    score_location         TINYINT UNSIGNED NULL,
    score_compensation     TINYINT UNSIGNED NULL,
    score_contract         TINYINT UNSIGNED NULL,
    classification         VARCHAR(16)     NOT NULL,
    recommendation         VARCHAR(10)     NOT NULL,
    strengths              JSON            NULL,
    gaps                   JSON            NULL,
    reasons                JSON            NULL,
    method                 VARCHAR(16)     NOT NULL,
    ai_analysis_id         BIGINT UNSIGNED NULL,
    created_at             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_match UNIQUE (user_id, profile_key, job_offer_id),
    CONSTRAINT fk_match_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_match_offer FOREIGN KEY (job_offer_id) REFERENCES job_offers (id) ON DELETE CASCADE,
    CONSTRAINT ck_match_class CHECK (classification IN ('HIGH_MATCH', 'MEDIUM_MATCH', 'LOW_MATCH', 'NOT_ELIGIBLE', 'NEEDS_REVIEW')),
    INDEX ix_match_rank (user_id, classification, score_total)
) ENGINE = InnoDB;
