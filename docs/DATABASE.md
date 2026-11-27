# JobHunter AI — Diseño de la base de datos

> Estado: propuesta de la Fase 0 (aprobada). Motor: MySQL 8 (InnoDB, `utf8mb4` /
> `utf8mb4_0900_ai_ci`); se usa 8.0.46 y el esquema sigue siendo compatible con 8.4.
> Migraciones: Flyway, `backend/src/main/resources/db/migration/V{n}__{desc}.sql`.

## 1. Convenciones

| Tema | Convención |
|------|------------|
| Claves primarias | `id BIGINT UNSIGNED AUTO_INCREMENT`. Los identificadores públicos que expone la API son los mismos IDs numéricos (con verificación de propiedad en cada acceso); se puede agregar más adelante un ULID `public_id CHAR(26)` si la enumeración llega a ser un problema. |
| Propiedad | Cada tabla con datos del usuario tiene `user_id BIGINT UNSIGNED NOT NULL` + FK `users(id)` + un índice que empieza por `user_id`. |
| Auditoría | `created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)`, `updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)`. Todas las marcas de tiempo se guardan en UTC (`hibernate.jdbc.time_zone=UTC`). |
| Borrado lógico | `deleted_at DATETIME(6) NULL` en el contenido editable por el usuario (elementos del perfil, currículums, cartas de presentación, postulaciones, búsquedas guardadas). No en logs ni en tablas de catálogo. Eliminación de la cuenta = **borrado físico** (privacidad). |
| Bloqueo optimista | `version INT NOT NULL DEFAULT 0` en las entidades editadas desde la interfaz. |
| Enums | `VARCHAR(32)` + restricción `CHECK` (legible, fácil de migrar) — no el `ENUM` de MySQL. |
| Dinero | `DECIMAL(12,2)` + `currency CHAR(3)` (ISO 4217) + `salary_period` (`HOUR`,`DAY`,`MONTH`,`YEAR`). Costos en USD `DECIMAL(12,6)`. |
| Países | `CHAR(2)` ISO 3166-1 alfa-2. Regiones: `VARCHAR(16)` (`LATAM`,`EUROPE`,`NORTH_AMERICA`,`ASIA`,`AFRICA`,`OCEANIA`,`WORLDWIDE`). |
| JSON | Columnas `JSON` solo para datos realmente semiestructurados (salidas de IA, entradas de actors, documentos de currículum, extractos del payload original). Todo lo que se usa para filtrar es una columna real. |
| Texto largo | `MEDIUMTEXT` para descripciones de ofertas y texto extraído de CVs. |
| Hashes | `BINARY(32)` SHA-256 (`content_hash`, `url_hash`, `token_hash`). |

## 2. Vista general de entidades

```
users ─┬─ user_consents
       ├─ refresh_tokens
       ├─ professional_profiles ─┬─ profile_experiences ─ (experiences)
       │                         ├─ profile_projects ──── (projects)
       │                         └─ profile_skills ────── (user_skills)
       ├─ experiences ─ experience_skills ─ skills
       ├─ projects ─ project_skills ─ skills
       ├─ educations, certifications, user_languages
       ├─ user_skills ─ skills (catalogue)
       ├─ job_preferences (+ job_preference_countries)
       ├─ stored_files
       ├─ resumes ─ resume_versions ─ resume_extractions
       ├─ cover_letters (versions inline)
       ├─ saved_searches ─ search_executions ─ search_execution_jobs ─ job_offers
       ├─ job_matches ─ job_offers
       ├─ applications ─ application_events, application_documents
       ├─ notifications
       └─ company_watchlist (ATS board tokens)

job_sources ─ job_source_executions ─ job_source_references ─ job_offers ─┬─ job_offer_skills ─ skills
apify_actors ─ (job_sources)                                            └─ job_requirements
ai_prompt_versions ─ ai_analyses
ai_task_routes
integration_settings, api_usage_logs, audit_logs, shedlock
```

## 3. Tablas

### 3.1 Identidad y consentimiento

```sql
CREATE TABLE users (
  id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  email           VARCHAR(254) NOT NULL,
  password_hash   VARCHAR(255) NOT NULL,
  full_name       VARCHAR(150) NOT NULL,
  role            VARCHAR(16)  NOT NULL DEFAULT 'USER',      -- USER | ADMIN
  status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',    -- ACTIVE | LOCKED | DISABLED
  locale          VARCHAR(10)  NOT NULL DEFAULT 'en',
  timezone        VARCHAR(64)  NOT NULL DEFAULT 'America/La_Paz',
  theme           VARCHAR(8)   NOT NULL DEFAULT 'system',
  failed_logins   INT NOT NULL DEFAULT 0,
  locked_until    DATETIME(6) NULL,
  last_login_at   DATETIME(6) NULL,
  created_at ..., updated_at ..., version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_users_email (email)
);

CREATE TABLE refresh_tokens (
  id, user_id FK, token_hash BINARY(32) NOT NULL, family_id CHAR(36) NOT NULL,
  expires_at DATETIME(6) NOT NULL, revoked_at DATETIME(6) NULL, replaced_by_id BIGINT UNSIGNED NULL,
  user_agent VARCHAR(255), ip VARBINARY(16), created_at,
  UNIQUE KEY uk_rt_hash (token_hash), KEY ix_rt_user (user_id, expires_at)
);

CREATE TABLE user_consents (               -- P7: per-provider consent to send CV data
  id, user_id FK, consent_type VARCHAR(40) NOT NULL,   -- AI_PROCESS_CV, AI_PROCESS_PROFILE, RESUME_PARSER_EXTERNAL
  provider VARCHAR(40) NOT NULL DEFAULT '*',           -- deepseek | openai | affinda | *
  granted BOOLEAN NOT NULL, granted_at DATETIME(6), revoked_at DATETIME(6),
  UNIQUE KEY uk_consent (user_id, consent_type, provider)
);
```

> `provider` también admite `claude` (ver `SECURITY.md` §7). El tipo de consentimiento
> `EMAIL_DIGEST` se eliminó junto con el envío de correos.

### 3.2 Perfil profesional

| Tabla | Columnas clave | Restricciones / índices |
|-------|----------------|-------------------------|
| `professional_profiles` | `user_id`, `name` ("Java Backend"), `headline`, `summary TEXT`, `target_role`, `is_default`, `github_url`, `linkedin_url`, `portfolio_url`, `website_url`, `phone`, `contact_email`, `city`, `country CHAR(2)`, borrado lógico, version | `uk (user_id, name, deleted_at)`; `ix (user_id)` |
| `experiences` | `user_id`, `company_name`, `company_url`, `position_title`, `employment_type` (`FULL_TIME`,`PART_TIME`,`CONTRACT`,`FREELANCE`,`INTERNSHIP`), `work_modality` (`REMOTE`,`HYBRID`,`ONSITE`), `location`, `country`, `start_date DATE`, `end_date DATE NULL`, `is_current`, `description TEXT`, `achievements JSON` (arreglo de `{id, text}`), `verified BOOLEAN`, `source` (`MANUAL`,`CV_IMPORT`) | `CHECK (end_date IS NULL OR end_date >= start_date)`; `ix (user_id, start_date)` |
| `projects` | `user_id`, `kind` (`PERSONAL`,`ACADEMIC`,`FREELANCE_CLIENT`,`OPEN_SOURCE`), `name`, `role`, `description`, `url`, `repo_url`, `start_date`, `end_date`, `highlights JSON`, `verified` | `ix (user_id, kind)` |
| `educations` | `user_id`, `institution`, `degree`, `field_of_study`, `start_date`, `end_date`, `status` (`COMPLETED`,`IN_PROGRESS`,`INCOMPLETE`), `description`, `verified` | `ix (user_id)` |
| `certifications` | `user_id`, `name`, `issuer`, `issued_at`, `expires_at`, `credential_id`, `credential_url`, `verified` | `ix (user_id)` |
| `skills` (catálogo compartido) | `id`, `name`, `normalized_name`, `category` (`PROGRAMMING_LANGUAGE`,`FRAMEWORK`,`DATABASE`,`CLOUD`,`TOOL`,`METHODOLOGY`,`OTHER`), `aliases JSON` | `uk (normalized_name)` |
| `user_skills` | `user_id`, `skill_id`, `evidence_level` (`PROFESSIONAL`,`PROJECT_ONLY`,`ACADEMIC`,`LEARNING`), `self_rating TINYINT` 1–5, `years_professional DECIMAL(4,1) NULL` (derivado), `last_used_year`, `verified` | `uk (user_id, skill_id)`; `ix (skill_id)` |
| `experience_skills`, `project_skills` | (`experience_id`/`project_id`, `skill_id`) | PK compuesta; `ix (skill_id)` |
| `user_languages` | `user_id`, `language_code` (ISO 639-1), `level` (`A1`..`C2`,`NATIVE`), `certificate`, `verified` | `uk (user_id, language_code)` |
| `profile_experiences`, `profile_projects`, `profile_skills` | `profile_id`, id del elemento, `sort_order`, `highlighted` | PK compuesta. Permite que cada perfil profesional seleccione/ordene elementos sin duplicarlos. |
| `job_preferences` | `user_id`, `profile_id NULL`, `regions JSON`, `modalities JSON`, `contract_types JSON`, `engagement_types JSON`, `min_salary`, `salary_currency`, `salary_period`, `work_languages JSON`, `seniority` (`JUNIOR`,`MID`,`SENIOR`), `relocation_ok` | `ix (user_id)` |
| `job_preference_countries` | `preference_id`, `country`, `mode` (`INCLUDE`,`EXCLUDE`) | PK compuesta |

> Los años de experiencia profesional se **calculan** a partir de las fechas de `experiences`;
> nunca se guardan como un número libre que la IA podría inflar.

### 3.3 Archivos, currículums, cartas de presentación

| Tabla | Columnas clave | Restricciones / índices |
|-------|----------------|-------------------------|
| `stored_files` | `user_id`, `storage_key CHAR(36)` (nombre de archivo UUID), `original_name`, `content_type`, `size_bytes`, `sha256 BINARY(32)`, `purpose` (`CV_UPLOAD`,`RESUME_PDF`,`COVER_LETTER_PDF`,`EXPORT`) | `uk (storage_key)`; `ix (user_id, purpose)` |
| `resumes` | `user_id`, `profile_id`, `title`, `language CHAR(2)`, `template_id`, `current_version_id`, borrado lógico | `ix (user_id)` |
| `resume_versions` | `resume_id`, `version_no INT`, `parent_version_id NULL`, `origin` (`UPLOAD`,`MANUAL`,`OPTIMIZED`,`TRANSLATED`), `job_offer_id NULL`, `language`, `content JSON` (currículum estructurado, cada viñeta con `sourceRefs`/`status`), `status` (`DRAFT`,`APPROVED`,`ARCHIVED`), `approved_at`, `pdf_file_id NULL`, `ai_analysis_id NULL`, `validation_report JSON` | `uk (resume_id, version_no)`; `ix (job_offer_id)`. Las filas son **inmutables** después de `APPROVED`. |
| `resume_extractions` | `user_id`, `file_id`, `extractor` (`PDFBOX`,`POI`,`AFFINDA`,`AI`), `status`, `raw_text MEDIUMTEXT`, `parsed JSON`, `error` | `ix (user_id, file_id)` |
| `cover_letters` | `user_id`, `job_offer_id NULL`, `application_id NULL`, `format` (`FORMAL_LETTER`,`SHORT_MESSAGE`,`EMAIL_INTRO`,`FREELANCE_PROPOSAL`,`LINKEDIN_MESSAGE`), `language`, `version_no`, `parent_id`, `subject`, `body MEDIUMTEXT`, `status`, `ai_analysis_id`, borrado lógico | `ix (user_id, job_offer_id)` |

### 3.4 Fuentes, ofertas, deduplicación

```sql
CREATE TABLE job_sources (                 -- one row per connector configuration
  id, code VARCHAR(40) NOT NULL,           -- remotive, greenhouse, lever, ashby, adzuna, serpapi_google_jobs, apify:<actorKey>
  type VARCHAR(24) NOT NULL,               -- PUBLIC_API | ATS_BOARD | SEARCH_API | APIFY_ACTOR
  display_name VARCHAR(100), enabled BOOLEAN NOT NULL DEFAULT FALSE,
  terms_url VARCHAR(500), terms_notes TEXT, attribution_required BOOLEAN NOT NULL DEFAULT FALSE,
  requests_per_minute INT, daily_request_cap INT, monthly_request_cap INT,
  created_at, updated_at, UNIQUE KEY uk_source_code (code)
);

CREATE TABLE apify_actors (
  id, job_source_id FK UNIQUE, actor_id VARCHAR(120) NOT NULL,   -- "username~actor-name"
  platform VARCHAR(40),                    -- linkedin, indeed, glassdoor, google_jobs, other
  input_template JSON NOT NULL,            -- with placeholders {{keywords}}, {{location}}, {{maxItems}}
  field_mapping JSON NOT NULL,             -- dataset item → JobOfferDraft paths
  max_items INT NOT NULL DEFAULT 50, max_charge_usd DECIMAL(8,2) NOT NULL DEFAULT 0.50,
  timeout_secs INT NOT NULL DEFAULT 300, memory_mb INT NULL,
  pricing_model VARCHAR(24), assessed_at DATETIME(6), assessment_notes TEXT, version
);

CREATE TABLE company_watchlist (            -- ATS boards need a known board token
  id, user_id NULL,                         -- NULL = global (admin)
  ats VARCHAR(16) NOT NULL,                 -- GREENHOUSE | LEVER | ASHBY
  board_token VARCHAR(120) NOT NULL, company_name VARCHAR(200), enabled BOOLEAN,
  last_checked_at DATETIME(6), last_status VARCHAR(16),
  UNIQUE KEY uk_watch (ats, board_token, user_id)
);

CREATE TABLE job_source_executions (        -- one connector call within a search run (or a watch-list sweep)
  id, job_source_id FK, search_execution_id NULL FK,
  status VARCHAR(16),                       -- RUNNING | SUCCEEDED | FAILED | TIMED_OUT | ABORTED | SKIPPED_BUDGET
  external_run_id VARCHAR(64) NULL,         -- Apify run id
  external_dataset_id VARCHAR(64) NULL,
  request_count INT, items_fetched INT, items_new INT, items_duplicate INT,
  cost_usd DECIMAL(12,6), started_at, finished_at, error_code VARCHAR(64), error_message TEXT,
  KEY ix_jse_source_time (job_source_id, started_at), KEY ix_jse_run (external_run_id)
);

CREATE TABLE job_offers (                   -- normalized, deduplicated vacancy
  id,
  title VARCHAR(300) NOT NULL, normalized_title VARCHAR(300) NOT NULL,
  company_name VARCHAR(200), normalized_company VARCHAR(200),
  description_html MEDIUMTEXT,              -- sanitized
  description_text MEDIUMTEXT,              -- plain text for AI / FULLTEXT
  location_text VARCHAR(300), country CHAR(2) NULL, region VARCHAR(16) NULL,
  work_modality VARCHAR(16),                -- REMOTE | HYBRID | ONSITE | UNKNOWN
  remote_scope VARCHAR(24),                 -- WORLDWIDE | LATAM | AMERICAS | EUROPE | COUNTRY_RESTRICTED | TIMEZONE_RESTRICTED | UNKNOWN
  remote_countries JSON NULL,               -- allowed countries when known
  bolivia_eligibility VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',  -- ELIGIBLE | LIKELY | NOT_ELIGIBLE | UNKNOWN
  eligibility_evidence TEXT NULL,           -- quoted text / rule that produced the decision
  engagement_type VARCHAR(24),              -- EMPLOYEE_LOCAL | EMPLOYEE_EOR | CONTRACTOR | FREELANCE_PROJECT | SPONSORED_RELOCATION | UNKNOWN
  contract_type VARCHAR(16),                -- FULL_TIME | PART_TIME | CONTRACT | TEMPORARY | FREELANCE | INTERNSHIP | UNKNOWN
  seniority VARCHAR(16),
  salary_min DECIMAL(12,2), salary_max DECIMAL(12,2), salary_currency CHAR(3), salary_period VARCHAR(8),
  salary_min_usd_month DECIMAL(12,2) NULL,  -- normalized for filtering (FX rate snapshot)
  salary_is_estimated BOOLEAN NOT NULL DEFAULT FALSE,
  required_languages JSON NULL,
  published_at DATETIME(6) NULL, expires_at DATETIME(6) NULL,
  canonical_url VARCHAR(1000), canonical_url_hash BINARY(32),
  apply_url VARCHAR(1000),
  content_hash BINARY(32) NOT NULL,         -- hash of normalized title+company+description
  fingerprint BINARY(32) NOT NULL,          -- hash(normalized_company | normalized_title | country/remote_scope)
  simhash BIGINT UNSIGNED NULL,             -- 64-bit SimHash of description for near-dup detection
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | EXPIRED | REMOVED | DUPLICATE
  duplicate_of_id BIGINT UNSIGNED NULL,
  first_seen_at DATETIME(6) NOT NULL, last_seen_at DATETIME(6) NOT NULL, last_verified_at DATETIME(6) NULL,
  created_at, updated_at,
  KEY ix_jo_fingerprint (fingerprint),
  KEY ix_jo_url_hash (canonical_url_hash),
  KEY ix_jo_status_published (status, published_at),
  KEY ix_jo_country_status (country, status, published_at),
  KEY ix_jo_region_modality (region, work_modality, status),
  KEY ix_jo_eligibility (bolivia_eligibility, status, published_at),
  KEY ix_jo_salary (salary_min_usd_month),
  KEY ix_jo_company (normalized_company),
  FULLTEXT KEY ft_jo_title_desc (title, description_text)
);

CREATE TABLE job_source_references (        -- spec §8: source info kept separately
  id, job_offer_id FK NOT NULL, job_source_id FK NOT NULL,
  external_id VARCHAR(200) NOT NULL,        -- id at the source (or hash of URL if none)
  source_url VARCHAR(1000) NOT NULL,        -- original publication URL, preserved verbatim
  apply_url VARCHAR(1000),
  attribution_text VARCHAR(300),            -- e.g. "via Remotive", "Jobs by Adzuna"
  raw_payload JSON NULL,                    -- trimmed original item (for re-normalization/debug), TTL-purged
  raw_content_hash BINARY(32),
  first_seen_at, last_seen_at, removed_at DATETIME(6) NULL,
  UNIQUE KEY uk_jsr_source_ext (job_source_id, external_id),
  KEY ix_jsr_offer (job_offer_id)
);

CREATE TABLE job_requirements (             -- extracted (deterministic dictionary or AI)
  id, job_offer_id FK, kind VARCHAR(16),    -- SKILL | LANGUAGE | EXPERIENCE | EDUCATION | CERTIFICATION | OTHER
  importance VARCHAR(12),                   -- MANDATORY | PREFERRED
  skill_id NULL FK, text VARCHAR(500), min_years DECIMAL(4,1) NULL, language_code CHAR(2) NULL, level VARCHAR(8) NULL,
  extracted_by VARCHAR(16),                 -- DICTIONARY | AI
  ai_analysis_id NULL,
  KEY ix_jr_offer (job_offer_id), KEY ix_jr_skill (skill_id, importance)
);

CREATE TABLE job_offer_skills (             -- denormalized tag table for fast tech filtering & analytics
  job_offer_id, skill_id, importance VARCHAR(12),
  PRIMARY KEY (job_offer_id, skill_id), KEY ix_jos_skill (skill_id, job_offer_id)
);

CREATE TABLE user_job_states (              -- per-user flags without creating an application
  user_id, job_offer_id, saved BOOLEAN, hidden BOOLEAN, seen_at DATETIME(6),
  PRIMARY KEY (user_id, job_offer_id)
);
```

### 3.5 Búsquedas y programación

| Tabla | Columnas clave | Restricciones / índices |
|-------|----------------|-------------------------|
| `saved_searches` | `user_id`, `name`, `profile_id NULL`, `filters JSON` (DTO validado), `keywords VARCHAR(300)`, `source_codes JSON` (fuentes activadas para esta búsqueda), `per_source_overrides JSON`, `schedule_type` (`MANUAL`,`INTERVAL`,`DAILY`,`CRON`), `interval_minutes` (mín. 60), `cron_expr`, `timezone`, `enabled`, `next_run_at`, `last_run_at`, `max_results_per_source`, `notify_on_new`, borrado lógico, version | `uk (user_id, name, deleted_at)`; `ix (enabled, next_run_at)` — consulta del despachador |
| `search_executions` | `saved_search_id`, `user_id`, `trigger` (`MANUAL`,`SCHEDULED`), `status`, `started_at`, `finished_at`, `jobs_found`, `jobs_new`, `jobs_duplicate`, `cost_usd`, `error_summary` | `ix (saved_search_id, started_at DESC)`; `ix (user_id, started_at)` |
| `search_execution_jobs` | `search_execution_id`, `job_offer_id`, `is_new BOOLEAN` | PK compuesta; `ix (job_offer_id)` |
| `shedlock` | tabla estándar de ShedLock (`name` PK, `lock_until`, `locked_at`, `locked_by`) | — |

> Las "búsquedas programadas" de la especificación §16 **no** son una tabla aparte: las columnas de
> programación viven en `saved_searches` y el historial en `search_executions`. Menos joins, la
> misma información.

### 3.6 IA y matching

| Tabla | Columnas clave | Restricciones / índices |
|-------|----------------|-------------------------|
| `ai_prompt_versions` | `prompt_key` (`JOB_ANALYSIS`,`MATCH_SEMANTIC`,`CV_PARSE`,`CV_OPTIMIZE`,`COVER_LETTER`,`SKILL_RECOMMENDATION`), `version INT`, `system_template TEXT`, `user_template TEXT`, `output_schema JSON`, `model_hint`, `temperature DECIMAL(3,2)`, `max_output_tokens`, `active BOOLEAN`, `checksum BINARY(32)`, `notes` | `uk (prompt_key, version)`; prompts sembrados por Flyway; cada edición crea una nueva versión |
| `ai_task_routes` | `prompt_key`, `provider` (`DEEPSEEK`,`CLAUDE`,`OPENAI`), `model`, `effort` (`LOW`..`MAX`, NULL para proveedores sin esfuerzo), `thinking` (`ON`/`OFF`/NULL), `fallback_provider`, `fallback_model`, `max_output_tokens`, `updated_by`, version | `uk (prompt_key)`; sembrada con rutas a DeepSeek; el usuario elige DeepSeek o Claude por tarea en Integraciones → AI routing |
| `ai_analyses` | `user_id NULL`, `prompt_version_id`, `provider`, `model`, `subject_type` (`JOB_OFFER`,`MATCH`,`RESUME`,`COVER_LETTER`), `subject_id`, `cache_key BINARY(32)` = sha256(checksum del prompt + hashes de entrada), `status` (`SUCCEEDED`,`FAILED`,`INVALID_OUTPUT`), `output JSON`, `input_tokens`, `cached_input_tokens`, `output_tokens`, `cost_usd`, `latency_ms`, `error` | `uk (cache_key)` para filas exitosas; `ix (subject_type, subject_id)`; `ix (user_id, created_at)` |
| `job_matches` | `user_id`, `profile_id`, `job_offer_id`, `profile_snapshot_hash BINARY(32)`, `job_content_hash`, `score_total TINYINT` 0–100, `score_skills`, `score_experience`, `score_language`, `score_seniority`, `score_location`, `score_compensation`, `score_contract`, `classification` (`HIGH_MATCH`,`MEDIUM_MATCH`,`LOW_MATCH`,`NOT_ELIGIBLE`,`NEEDS_REVIEW`), `strengths JSON`, `gaps JSON`, `reasons JSON`, `recommendation` (`APPLY`,`CONSIDER`,`SKIP`,`REVIEW`), `ai_analysis_id NULL`, `stale BOOLEAN` | `uk (profile_id, job_offer_id)`; `ix (user_id, classification, score_total DESC)` |

### 3.7 Postulaciones y notificaciones

| Tabla | Columnas clave | Restricciones / índices |
|-------|----------------|-------------------------|
| `applications` | `user_id`, `job_offer_id NULL` (se permiten postulaciones manuales), `company_name`, `position_title`, `job_url`, `source_label`, `status`, `method` (`EMAIL`,`EXTERNAL_FORM`,`CAREER_PORTAL`,`LINKEDIN`,`FREELANCE_PLATFORM`,`OTHER`), `resume_version_id NULL`, `cover_letter_id NULL`, `salary_expectation`, `salary_currency`, `salary_period`, `applied_at`, `follow_up_at`, `notes TEXT`, `kanban_order INT`, borrado lógico, version | `uk (user_id, job_offer_id, deleted_at)`; `ix (user_id, status, kanban_order)`; `ix (user_id, follow_up_at)` |
| `application_events` | `application_id`, `type` (`STATUS_CHANGE`,`NOTE`,`EMAIL_RECEIVED`,`INTERVIEW_SCHEDULED`,`SUBMISSION_CONFIRMED`,`FOLLOW_UP`), `from_status`, `to_status`, `payload JSON`, `occurred_at`, `created_by` | `ix (application_id, occurred_at)`. "Marcar como enviada" crea un evento `SUBMISSION_CONFIRMED` (con el método y los documentos usados en `payload`) y fija `applications.applied_at`. |
| `application_documents` | `application_id`, `kind` (`RESUME_PDF`,`COVER_LETTER`,`PORTFOLIO`,`OTHER`), `file_id`, `resume_version_id`, `cover_letter_id` | `ix (application_id)` |
| `notifications` | `user_id`, `type`, `title`, `body`, `link`, `read_at`, `payload JSON` | `ix (user_id, read_at, created_at)` |

> Las tablas `email_logs` y `application_approvals` se eliminaron: el envío de correos se descartó
> del alcance (decisión del usuario, 2026-11-26). Como la aplicación ya no envía nada, no hace falta
> un registro de aprobación aparte; la confirmación del usuario queda registrada en el evento
> `SUBMISSION_CONFIRMED` y en `audit_logs`.

### 3.8 Administración, uso, auditoría

| Tabla | Columnas clave | Restricciones / índices |
|-------|----------------|-------------------------|
| `integration_settings` | `integration_code` (`DEEPSEEK`,`CLAUDE`,`OPENAI`,`APIFY`,`ADZUNA`,`REMOTIVE`,`SERPAPI`,`JOOBLE`,`HIMALAYAS`,`AFFINDA`; una fila por integración; las fuentes gratuitas sin clave también tienen fila para poder activarlas/desactivarlas), `user_id NULL` (NULL = sistema), `enabled`, `config JSON` (no secreto: modelo, URL base de la lista de permitidos, límites), `secret_ciphertext VARBINARY(4096) NULL` (AES-256-GCM), `secret_key_version INT`, `secret_last4 CHAR(4)`, `daily_limit`, `monthly_limit`, `monthly_budget_usd`, `status` (`UNKNOWN`,`OK`,`ERROR`,`DISABLED`), `last_success_at`, `last_error_at`, `last_error`, version | `uk (integration_code, user_id)` |
| `api_usage_logs` | `integration_code`, `user_id NULL`, `operation` (`SEARCH`,`ACTOR_RUN`,`CHAT_COMPLETION`,…), `request_count`, `input_tokens`, `output_tokens`, `cached_tokens`, `result_count`, `cost_usd`, `cost_is_estimate BOOLEAN`, `http_status`, `latency_ms`, `occurred_at`, `correlation_id` | `ix (integration_code, occurred_at)`; `ix (user_id, occurred_at)`. Se agrega por mes para los presupuestos. Particionar por mes más adelante si supera 10M de filas. |
| `fx_rates` | `currency CHAR(3)`, `rate_to_usd DECIMAL(18,8)`, `as_of DATE` | `pk (currency, as_of)` — para normalizar salarios (cargado manualmente/por el administrador al inicio) |
| `audit_logs` | `actor_user_id`, `action` (`LOGIN`,`LOGIN_FAILED`,`SETTINGS_CHANGED`,`SECRET_UPDATED`,`DATA_EXPORTED`,`ACCOUNT_DELETED`,…), `target_type`, `target_id`, `ip`, `user_agent`, `details JSON`, `occurred_at` | `ix (actor_user_id, occurred_at)`; `ix (action, occurred_at)` |

## 4. Estrategia de deduplicación (especificación §8)

Se evalúa en orden durante la ingesta; gana la primera coincidencia:

1. **Misma fuente + id externo** → `uk_jsr_source_ext` → se actualiza la referencia existente
   (`last_seen_at`; si cambia el contenido → se recalcula el hash y se marcan las coincidencias como
   `stale`).
2. **URL canónica** → coincidencia de `canonical_url_hash`. Canonización: host en minúsculas, se
   quitan los parámetros de seguimiento (`utm_*`, `ref`, `source`, `gh_src`, …), se quita el
   fragmento y se normaliza la barra final. Para URLs de ATS, se extrae el id de la oferta en el ATS
   (Greenhouse `/jobs/{id}`, UUID de Lever, UUID de Ashby).
3. **Huella** (empresa normalizada + título normalizado + país/alcance remoto) → candidatas; se
   confirma con **distancia de Hamming de SimHash ≤ 3** sobre la descripción (o Jaccard ≥ 0.9 sobre
   shingles de 5 palabras cuando una descripción está truncada). Fechas de publicación dentro de 45
   días.
4. Si no hay coincidencia → nueva `job_offer`.

Una oferta fusionada recibe una fila adicional en `job_source_references`: la fuente, la URL y la
atribución originales nunca se pierden. Las coincidencias cercanas ambiguas (distancia 4–8) se
vinculan como candidatas `duplicate_of_id` para revisión del administrador, en lugar de fusionarse
automáticamente.

Normalización: minúsculas, Unicode NFKD + eliminación de acentos, eliminación de sufijos legales
(`inc`, `llc`, `gmbh`, `s.a.`, `s.r.l.`, `ltd`…), unificación de sinónimos de seniority (`sr.` →
`senior`), eliminación de marcadores de género comunes en ofertas en DE/ES/IT (`(m/w/d)`, `(h/m)`).

**Vencimiento:** una referencia que su fuente no devuelve durante N ejecuciones consecutivas de la
misma consulta (por defecto 3), o cuyo `verify()` devuelve 404/cerrada → `removed_at`. Una oferta
cuyas referencias están todas retiradas, o cuyo `expires_at` ya pasó → `EXPIRED`. Los portales ATS
(listados completos por empresa) permiten detectar el retiro con exactitud; las APIs de búsqueda
solo permiten "no vista recientemente".

## 5. Patrones de consulta e índices

| Consulta | Índice usado |
|----------|--------------|
| Lista de Descubrir: status=ACTIVE + país/región + modalidad + published_at desc | `ix_jo_country_status`, `ix_jo_region_modality` |
| Filtro "Accesible desde Bolivia" | `ix_jo_eligibility` |
| Filtro por tecnología (`Java AND Spring`) | `job_offer_skills ix_jos_skill` (semi-join por habilidad) |
| Búsqueda por palabra clave | `FULLTEXT ft_jo_title_desc` (`MATCH … AGAINST` en modo booleano) |
| Salario ≥ X USD/mes | `ix_jo_salary` |
| Ofertas recomendadas para el perfil | `job_matches ix (user_id, classification, score_total)` |
| Despachador | `saved_searches ix (enabled, next_run_at)` |
| Deduplicación | `uk_jsr_source_ext`, `ix_jo_url_hash`, `ix_jo_fingerprint` |
| Historial de postulaciones | `applications ix (user_id, status, kanban_order)`, `application_events ix (application_id, occurred_at)` |
| Costo mensual | `api_usage_logs ix (integration_code, occurred_at)` |

> FULLTEXT de MySQL en InnoDB usa el parser integrado con stopwords; con contenido en varios
> idiomas, `innodb_ft_min_token_size` (por defecto 3) hace que "Go", "C#"… no se indexen; por eso
> los filtros por tecnología se basan en `job_offer_skills`, no en FULLTEXT.

## 6. Retención de datos

| Dato | Retención |
|------|-----------|
| `job_source_references.raw_payload` | 90 días; luego se pone en NULL |
| `job_offers` EXPIRED/REMOVED | 12 meses (analítica); luego se eliminan, salvo que una postulación las referencie |
| `api_usage_logs` | 24 meses |
| `audit_logs` | 24 meses |
| CVs cargados / PDFs generados | hasta que el usuario los elimine o elimine la cuenta |

## 7. Plan de migraciones

| Migración | Fase | Contenido |
|-----------|------|-----------|
| `V1__identity.sql` | 1 | users, refresh_tokens, user_consents, audit_logs, shedlock |
| `V2__profile.sql` | 1 | professional_profiles, experiences, projects, educations, certifications, skills, user_skills, *_skills, user_languages, profile_*, job_preferences |
| `V3__skills_seed.sql` | 1 | catálogo de tecnologías + alias (≈ 400 entradas) |
| `V4__files_resumes.sql` | 2 | stored_files, resumes, resume_versions, resume_extractions |
| `V5__jobs.sql` | 3 | job_sources, apify_actors, company_watchlist, job_offers, job_source_references, job_requirements, job_offer_skills, user_job_states, fx_rates |
| `V6__searches.sql` | 3 | saved_searches, search_executions, search_execution_jobs, job_source_executions |
| `V7__integrations_usage.sql` | 3 | integration_settings (filas iniciales: APIFY **enabled=false**; ADZUNA, SERPAPI enabled=false hasta que se agreguen credenciales; REMOTIVE enabled=true), api_usage_logs |
| `V8__ai.sql` | 4 | ai_prompt_versions (+ prompts iniciales), ai_task_routes (+ siembra con DeepSeek), ai_analyses, job_matches; filas de integración DEEPSEEK y CLAUDE |
| `V9__cover_letters.sql` | 5 | cover_letters |
| `V10__applications.sql` | 6 | applications, application_events, application_documents, notifications |
