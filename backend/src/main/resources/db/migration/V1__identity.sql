-- Identity, consents, audit, scheduler lock.

CREATE TABLE users (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    email          VARCHAR(254) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    full_name      VARCHAR(150) NOT NULL,
    role           VARCHAR(16)  NOT NULL DEFAULT 'USER',
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    locale         VARCHAR(10)  NOT NULL DEFAULT 'es',
    timezone       VARCHAR(64)  NOT NULL DEFAULT 'America/La_Paz',
    theme          VARCHAR(8)   NOT NULL DEFAULT 'system',
    failed_logins  INT          NOT NULL DEFAULT 0,
    locked_until   DATETIME(6)  NULL,
    last_login_at  DATETIME(6)  NULL,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version        INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
    CONSTRAINT ck_users_theme CHECK (theme IN ('light', 'dark', 'system'))
) ENGINE = InnoDB;

CREATE TABLE refresh_tokens (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT UNSIGNED NOT NULL,
    token_hash      BINARY(32)      NOT NULL,
    family_id       CHAR(36)        NOT NULL,
    expires_at      DATETIME(6)     NOT NULL,
    revoked_at      DATETIME(6)     NULL,
    replaced_by_id  BIGINT UNSIGNED NULL,
    user_agent      VARCHAR(255)    NULL,
    ip              VARCHAR(45)     NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_rt_hash UNIQUE (token_hash),
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX ix_rt_user (user_id, expires_at),
    INDEX ix_rt_family (family_id)
) ENGINE = InnoDB;

CREATE TABLE user_consents (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT UNSIGNED NOT NULL,
    consent_type  VARCHAR(40)     NOT NULL,
    provider      VARCHAR(40)     NOT NULL,
    granted       BOOLEAN         NOT NULL,
    granted_at    DATETIME(6)     NULL,
    revoked_at    DATETIME(6)     NULL,
    created_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_consent UNIQUE (user_id, consent_type, provider),
    CONSTRAINT fk_consent_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE audit_logs (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    actor_user_id  BIGINT UNSIGNED NULL,
    action         VARCHAR(40)     NOT NULL,
    target_type    VARCHAR(40)     NULL,
    target_id      VARCHAR(64)     NULL,
    ip             VARCHAR(45)     NULL,
    user_agent     VARCHAR(255)    NULL,
    details        JSON            NULL,
    occurred_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX ix_audit_actor (actor_user_id, occurred_at),
    INDEX ix_audit_action (action, occurred_at)
) ENGINE = InnoDB;

CREATE TABLE shedlock (
    name        VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until  TIMESTAMP(3) NOT NULL,
    locked_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by   VARCHAR(255) NOT NULL
) ENGINE = InnoDB;
