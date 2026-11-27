-- Professional profile: experiences, projects, education, certifications, skills, languages, preferences.

CREATE TABLE skills (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    normalized_name  VARCHAR(100) NOT NULL,
    category         VARCHAR(24)  NOT NULL,
    aliases          JSON         NULL,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_skills_norm UNIQUE (normalized_name),
    CONSTRAINT ck_skills_category CHECK (category IN
        ('PROGRAMMING_LANGUAGE', 'FRAMEWORK', 'DATABASE', 'CLOUD', 'TOOL', 'METHODOLOGY', 'OTHER'))
) ENGINE = InnoDB;

CREATE TABLE professional_profiles (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT UNSIGNED NOT NULL,
    name           VARCHAR(100)    NOT NULL,
    headline       VARCHAR(200)    NULL,
    summary        TEXT            NULL,
    target_role    VARCHAR(150)    NULL,
    is_default     BOOLEAN         NOT NULL DEFAULT FALSE,
    contact_email  VARCHAR(254)    NULL,
    phone          VARCHAR(40)     NULL,
    city           VARCHAR(100)    NULL,
    country        CHAR(2)         NULL,
    github_url     VARCHAR(500)    NULL,
    linkedin_url   VARCHAR(500)    NULL,
    portfolio_url  VARCHAR(500)    NULL,
    website_url    VARCHAR(500)    NULL,
    created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at     DATETIME(6)     NULL,
    version        INT             NOT NULL DEFAULT 0,
    CONSTRAINT fk_pp_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX ix_pp_user (user_id, deleted_at)
) ENGINE = InnoDB;

CREATE TABLE experiences (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT UNSIGNED NOT NULL,
    company_name     VARCHAR(200)    NOT NULL,
    company_url      VARCHAR(500)    NULL,
    position_title   VARCHAR(200)    NOT NULL,
    employment_type  VARCHAR(16)     NOT NULL,
    work_modality    VARCHAR(8)      NULL,
    location         VARCHAR(200)    NULL,
    country          CHAR(2)         NULL,
    start_date       DATE            NOT NULL,
    end_date         DATE            NULL,
    is_current       BOOLEAN         NOT NULL DEFAULT FALSE,
    description      TEXT            NULL,
    achievements     JSON            NULL,
    verified         BOOLEAN         NOT NULL DEFAULT FALSE,
    source           VARCHAR(16)     NOT NULL DEFAULT 'MANUAL',
    created_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at       DATETIME(6)     NULL,
    version          INT             NOT NULL DEFAULT 0,
    CONSTRAINT fk_exp_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_exp_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_exp_type CHECK (employment_type IN ('FULL_TIME', 'PART_TIME', 'CONTRACT', 'FREELANCE', 'INTERNSHIP')),
    INDEX ix_exp_user (user_id, deleted_at, start_date)
) ENGINE = InnoDB;

CREATE TABLE projects (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT UNSIGNED NOT NULL,
    kind         VARCHAR(20)     NOT NULL,
    name         VARCHAR(200)    NOT NULL,
    role         VARCHAR(150)    NULL,
    description  TEXT            NULL,
    url          VARCHAR(500)    NULL,
    repo_url     VARCHAR(500)    NULL,
    start_date   DATE            NULL,
    end_date     DATE            NULL,
    highlights   JSON            NULL,
    verified     BOOLEAN         NOT NULL DEFAULT FALSE,
    source       VARCHAR(16)     NOT NULL DEFAULT 'MANUAL',
    created_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at   DATETIME(6)     NULL,
    version      INT             NOT NULL DEFAULT 0,
    CONSTRAINT fk_prj_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_prj_kind CHECK (kind IN ('PERSONAL', 'ACADEMIC', 'FREELANCE_CLIENT', 'OPEN_SOURCE')),
    CONSTRAINT ck_prj_dates CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date),
    INDEX ix_prj_user (user_id, deleted_at, kind)
) ENGINE = InnoDB;

CREATE TABLE educations (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT UNSIGNED NOT NULL,
    institution     VARCHAR(200)    NOT NULL,
    degree          VARCHAR(200)    NULL,
    field_of_study  VARCHAR(200)    NULL,
    start_date      DATE            NULL,
    end_date        DATE            NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'COMPLETED',
    description     TEXT            NULL,
    verified        BOOLEAN         NOT NULL DEFAULT FALSE,
    source          VARCHAR(16)     NOT NULL DEFAULT 'MANUAL',
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at      DATETIME(6)     NULL,
    version         INT             NOT NULL DEFAULT 0,
    CONSTRAINT fk_edu_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_edu_status CHECK (status IN ('COMPLETED', 'IN_PROGRESS', 'INCOMPLETE')),
    INDEX ix_edu_user (user_id, deleted_at)
) ENGINE = InnoDB;

CREATE TABLE certifications (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT UNSIGNED NOT NULL,
    name            VARCHAR(200)    NOT NULL,
    issuer          VARCHAR(200)    NULL,
    issued_at       DATE            NULL,
    expires_at      DATE            NULL,
    credential_id   VARCHAR(200)    NULL,
    credential_url  VARCHAR(500)    NULL,
    verified        BOOLEAN         NOT NULL DEFAULT FALSE,
    source          VARCHAR(16)     NOT NULL DEFAULT 'MANUAL',
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at      DATETIME(6)     NULL,
    version         INT             NOT NULL DEFAULT 0,
    CONSTRAINT fk_cert_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX ix_cert_user (user_id, deleted_at)
) ENGINE = InnoDB;

CREATE TABLE user_skills (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT UNSIGNED NOT NULL,
    skill_id        BIGINT UNSIGNED NOT NULL,
    evidence_level  VARCHAR(16)     NOT NULL,
    self_rating     TINYINT         NULL,
    last_used_year  SMALLINT        NULL,
    verified        BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_user_skill UNIQUE (user_id, skill_id),
    CONSTRAINT fk_us_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_us_skill FOREIGN KEY (skill_id) REFERENCES skills (id),
    CONSTRAINT ck_us_evidence CHECK (evidence_level IN ('PROFESSIONAL', 'PROJECT_ONLY', 'ACADEMIC', 'LEARNING')),
    CONSTRAINT ck_us_rating CHECK (self_rating IS NULL OR self_rating BETWEEN 1 AND 5),
    INDEX ix_us_skill (skill_id)
) ENGINE = InnoDB;

CREATE TABLE experience_skills (
    experience_id  BIGINT UNSIGNED NOT NULL,
    skill_id       BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (experience_id, skill_id),
    CONSTRAINT fk_es_exp FOREIGN KEY (experience_id) REFERENCES experiences (id) ON DELETE CASCADE,
    CONSTRAINT fk_es_skill FOREIGN KEY (skill_id) REFERENCES skills (id),
    INDEX ix_es_skill (skill_id)
) ENGINE = InnoDB;

CREATE TABLE project_skills (
    project_id  BIGINT UNSIGNED NOT NULL,
    skill_id    BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (project_id, skill_id),
    CONSTRAINT fk_ps_prj FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_ps_skill FOREIGN KEY (skill_id) REFERENCES skills (id),
    INDEX ix_ps_skill (skill_id)
) ENGINE = InnoDB;

CREATE TABLE user_languages (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT UNSIGNED NOT NULL,
    language_code  CHAR(2)         NOT NULL,
    level          VARCHAR(8)      NOT NULL,
    certificate    VARCHAR(200)    NULL,
    verified       BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_user_lang UNIQUE (user_id, language_code),
    CONSTRAINT fk_ul_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_ul_level CHECK (level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2', 'NATIVE'))
) ENGINE = InnoDB;

-- Items selected (and ordered) for each professional profile; no duplication of the items themselves.
CREATE TABLE profile_experiences (
    profile_id     BIGINT UNSIGNED NOT NULL,
    experience_id  BIGINT UNSIGNED NOT NULL,
    sort_order     INT             NOT NULL,
    PRIMARY KEY (profile_id, experience_id),
    CONSTRAINT fk_pe_profile FOREIGN KEY (profile_id) REFERENCES professional_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_pe_exp FOREIGN KEY (experience_id) REFERENCES experiences (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE profile_projects (
    profile_id  BIGINT UNSIGNED NOT NULL,
    project_id  BIGINT UNSIGNED NOT NULL,
    sort_order  INT             NOT NULL,
    PRIMARY KEY (profile_id, project_id),
    CONSTRAINT fk_ppr_profile FOREIGN KEY (profile_id) REFERENCES professional_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_ppr_prj FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE profile_skills (
    profile_id  BIGINT UNSIGNED NOT NULL,
    skill_id    BIGINT UNSIGNED NOT NULL,
    sort_order  INT             NOT NULL,
    PRIMARY KEY (profile_id, skill_id),
    CONSTRAINT fk_psk_profile FOREIGN KEY (profile_id) REFERENCES professional_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_psk_skill FOREIGN KEY (skill_id) REFERENCES skills (id)
) ENGINE = InnoDB;

CREATE TABLE job_preferences (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT UNSIGNED NOT NULL,
    regions            JSON            NULL,
    countries_include  JSON            NULL,
    countries_exclude  JSON            NULL,
    modalities         JSON            NULL,
    contract_types     JSON            NULL,
    engagement_types   JSON            NULL,
    min_salary         DECIMAL(12, 2)  NULL,
    salary_currency    CHAR(3)         NULL,
    salary_period      VARCHAR(8)      NULL,
    work_languages     JSON            NULL,
    seniority          VARCHAR(8)      NULL,
    relocation_ok      BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_jp_user UNIQUE (user_id),
    CONSTRAINT fk_jp_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;
