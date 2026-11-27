-- Phase 5: cover letters (versioned by letter group) + AI routes for the new tasks.

CREATE TABLE cover_letters (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT UNSIGNED NOT NULL,
    letter_group_id    BIGINT UNSIGNED NULL,
    job_offer_id       BIGINT UNSIGNED NULL,
    format             VARCHAR(24)     NOT NULL,
    language           CHAR(2)         NOT NULL,
    tone               VARCHAR(10)     NOT NULL DEFAULT 'NEUTRAL',
    version_no         INT             NOT NULL,
    parent_id          BIGINT UNSIGNED NULL,
    status             VARCHAR(10)     NOT NULL DEFAULT 'DRAFT',
    subject            VARCHAR(300)    NULL,
    body               MEDIUMTEXT      NOT NULL,
    suggestions        JSON            NULL,
    validation_report  JSON            NULL,
    ai_analysis_id     BIGINT UNSIGNED NULL,
    provider           VARCHAR(16)     NULL,
    model              VARCHAR(64)     NULL,
    approved_at        DATETIME(6)     NULL,
    created_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_cl_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_cl_offer FOREIGN KEY (job_offer_id) REFERENCES job_offers (id) ON DELETE SET NULL,
    CONSTRAINT ck_cl_format CHECK (format IN ('FORMAL_LETTER', 'SHORT_MESSAGE', 'EMAIL_INTRO', 'FREELANCE_PROPOSAL', 'LINKEDIN_MESSAGE')),
    CONSTRAINT ck_cl_status CHECK (status IN ('DRAFT', 'APPROVED')),
    CONSTRAINT ck_cl_tone CHECK (tone IN ('FORMAL', 'NEUTRAL', 'FRIENDLY')),
    INDEX ix_cl_user (user_id, job_offer_id),
    INDEX ix_cl_group (letter_group_id, version_no)
) ENGINE = InnoDB;

ALTER TABLE ai_task_routes MODIFY prompt_key VARCHAR(32) NOT NULL;
INSERT INTO ai_task_routes (prompt_key, provider, model, effort, thinking, max_output_tokens) VALUES
('SKILL_RECOMMENDATION', 'DEEPSEEK', 'deepseek-flash', NULL, 'OFF', 3000);
