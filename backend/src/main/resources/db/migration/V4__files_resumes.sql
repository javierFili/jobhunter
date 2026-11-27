-- Phase 2: stored files, CV uploads/extractions, resume documents with immutable versions.

CREATE TABLE stored_files (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT UNSIGNED NOT NULL,
    storage_key    CHAR(36)        NOT NULL,
    original_name  VARCHAR(255)    NOT NULL,
    content_type   VARCHAR(100)    NOT NULL,
    size_bytes     BIGINT          NOT NULL,
    sha256         BINARY(32)      NOT NULL,
    purpose        VARCHAR(24)     NOT NULL,
    created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_sf_key UNIQUE (storage_key),
    CONSTRAINT fk_sf_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_sf_purpose CHECK (purpose IN ('CV_UPLOAD', 'RESUME_PDF', 'COVER_LETTER_PDF', 'EXPORT')),
    INDEX ix_sf_user (user_id, purpose)
) ENGINE = InnoDB;

CREATE TABLE resume_extractions (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT UNSIGNED NOT NULL,
    file_id     BIGINT UNSIGNED NOT NULL,
    extractor   VARCHAR(16)     NOT NULL,
    status      VARCHAR(16)     NOT NULL,
    raw_text    MEDIUMTEXT      NULL,
    parsed      JSON            NULL,
    parser      VARCHAR(16)     NULL,
    error       VARCHAR(500)    NULL,
    imported_at DATETIME(6)     NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_re_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_re_file FOREIGN KEY (file_id) REFERENCES stored_files (id) ON DELETE CASCADE,
    CONSTRAINT ck_re_extractor CHECK (extractor IN ('PDFBOX', 'POI', 'AFFINDA', 'AI')),
    CONSTRAINT ck_re_status CHECK (status IN ('SUCCEEDED', 'FAILED')),
    INDEX ix_re_user (user_id, created_at)
) ENGINE = InnoDB;

CREATE TABLE resumes (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT UNSIGNED NOT NULL,
    profile_id          BIGINT UNSIGNED NULL,
    title               VARCHAR(150)    NOT NULL,
    language            CHAR(2)         NOT NULL,
    template_id         VARCHAR(32)     NOT NULL,
    current_version_id  BIGINT UNSIGNED NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at          DATETIME(6)     NULL,
    version             INT             NOT NULL DEFAULT 0,
    CONSTRAINT fk_res_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_res_profile FOREIGN KEY (profile_id) REFERENCES professional_profiles (id) ON DELETE SET NULL,
    INDEX ix_res_user (user_id, deleted_at)
) ENGINE = InnoDB;

CREATE TABLE resume_versions (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    resume_id          BIGINT UNSIGNED NOT NULL,
    version_no         INT             NOT NULL,
    parent_version_id  BIGINT UNSIGNED NULL,
    origin             VARCHAR(16)     NOT NULL,
    job_offer_id       BIGINT UNSIGNED NULL,
    language           CHAR(2)         NOT NULL,
    content            JSON            NOT NULL,
    status             VARCHAR(16)     NOT NULL DEFAULT 'DRAFT',
    approved_at        DATETIME(6)     NULL,
    pdf_file_id        BIGINT UNSIGNED NULL,
    ai_analysis_id     BIGINT UNSIGNED NULL,
    validation_report  JSON            NULL,
    note               VARCHAR(300)    NULL,
    created_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_rv_no UNIQUE (resume_id, version_no),
    CONSTRAINT fk_rv_resume FOREIGN KEY (resume_id) REFERENCES resumes (id) ON DELETE CASCADE,
    CONSTRAINT fk_rv_parent FOREIGN KEY (parent_version_id) REFERENCES resume_versions (id) ON DELETE SET NULL,
    CONSTRAINT fk_rv_pdf FOREIGN KEY (pdf_file_id) REFERENCES stored_files (id) ON DELETE SET NULL,
    CONSTRAINT ck_rv_origin CHECK (origin IN ('UPLOAD', 'PROFILE', 'MANUAL', 'OPTIMIZED', 'TRANSLATED')),
    CONSTRAINT ck_rv_status CHECK (status IN ('DRAFT', 'APPROVED', 'ARCHIVED')),
    INDEX ix_rv_job (job_offer_id)
) ENGINE = InnoDB;
