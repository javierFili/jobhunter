-- Phase 3: integration settings (on/off + write-only encrypted credentials + limits) and usage accounting.

CREATE TABLE integration_settings (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    integration_code    VARCHAR(24)     NOT NULL,
    enabled             BOOLEAN         NOT NULL DEFAULT FALSE,
    config              JSON            NULL,
    secret_ciphertext   VARBINARY(4096) NULL,
    secret_key_version  INT             NOT NULL DEFAULT 1,
    secret_last4        VARCHAR(4)      NULL,
    daily_limit         INT             NULL,
    monthly_limit       INT             NULL,
    monthly_budget_usd  DECIMAL(10, 2)  NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'UNKNOWN',
    last_success_at     DATETIME(6)     NULL,
    last_error_at       DATETIME(6)     NULL,
    last_error          VARCHAR(1000)   NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version             INT             NOT NULL DEFAULT 0,
    CONSTRAINT uk_integration UNIQUE (integration_code),
    CONSTRAINT ck_integration_status CHECK (status IN ('UNKNOWN', 'OK', 'ERROR', 'DISABLED'))
) ENGINE = InnoDB;

-- Initial state (user decision 2026-11-26): Apify OFF; keyed sources OFF until credentials are entered;
-- keyless public sources ON. AI providers are added in V8.
INSERT INTO integration_settings (integration_code, enabled, config, daily_limit, monthly_limit, monthly_budget_usd) VALUES
('APIFY',     FALSE, '{}', NULL, NULL, 5.00),
('ADZUNA',    FALSE, '{"defaultCountry":"us"}', 50, 1000, NULL),
('SERPAPI',   FALSE, '{"maxPagesPerSearch":2}', 50, 250, NULL),
('REMOTIVE',  TRUE,  '{}', 4, NULL, NULL),
('HIMALAYAS', TRUE,  '{}', 500, NULL, NULL),
('JOBICY',    TRUE,  '{}', 50, NULL, NULL),
('REMOTEOK',  TRUE,  '{}', 4, NULL, NULL),
('GREENHOUSE', TRUE, '{}', 500, NULL, NULL),
('LEVER',     TRUE,  '{}', 500, NULL, NULL),
('ASHBY',     TRUE,  '{}', 500, NULL, NULL);

CREATE TABLE api_usage_logs (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    integration_code  VARCHAR(24)     NOT NULL,
    user_id           BIGINT UNSIGNED NULL,
    operation         VARCHAR(32)     NOT NULL,
    request_count     INT             NOT NULL DEFAULT 1,
    input_tokens      INT             NULL,
    cached_tokens     INT             NULL,
    output_tokens     INT             NULL,
    result_count      INT             NULL,
    cost_usd          DECIMAL(12, 6)  NOT NULL DEFAULT 0,
    cost_is_estimate  BOOLEAN         NOT NULL DEFAULT TRUE,
    http_status       INT             NULL,
    success           BOOLEAN         NOT NULL DEFAULT TRUE,
    latency_ms        INT             NULL,
    correlation_id    VARCHAR(64)     NULL,
    occurred_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX ix_usage_integration (integration_code, occurred_at),
    INDEX ix_usage_user (user_id, occurred_at)
) ENGINE = InnoDB;
