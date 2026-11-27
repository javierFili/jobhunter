package com.jobhunter.privacy.application;

import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.audit.AuditService;
import com.jobhunter.shared.files.FileStorageService;
import com.jobhunter.shared.files.StoredFile;
import com.jobhunter.shared.files.StoredFileRepository;
import com.jobhunter.shared.json.Json;
import com.jobhunter.users.application.UserService;
import com.jobhunter.users.domain.User;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Data rights: full export (ZIP with one JSON per table + the user's files) and irreversible account deletion.
 * Exports never include the password hash, refresh tokens or system integration credentials.
 */
@Service
public class PrivacyService {

    /** user-owned tables exported with "WHERE user_id = ?" (column lists exclude secrets). */
    private static final Map<String, String> USER_TABLES = new LinkedHashMap<>();
    /** child tables exported through their parent. */
    private static final Map<String, String> CHILD_QUERIES = new LinkedHashMap<>();

    static {
        USER_TABLES.put("user_consents", "*");
        USER_TABLES.put("professional_profiles", "*");
        USER_TABLES.put("experiences", "*");
        USER_TABLES.put("projects", "*");
        USER_TABLES.put("educations", "*");
        USER_TABLES.put("certifications", "*");
        USER_TABLES.put("user_skills", "*");
        USER_TABLES.put("user_languages", "*");
        USER_TABLES.put("job_preferences", "*");
        USER_TABLES.put("stored_files", "id, storage_key, original_name, content_type, size_bytes, purpose, created_at");
        USER_TABLES.put("resume_extractions", "*");
        USER_TABLES.put("resumes", "*");
        USER_TABLES.put("cover_letters", "*");
        USER_TABLES.put("saved_searches", "*");
        USER_TABLES.put("search_executions", "*");
        USER_TABLES.put("company_watchlist", "*");
        USER_TABLES.put("user_job_states", "*");
        USER_TABLES.put("job_matches", "id, user_id, profile_key, job_offer_id, score_total, score_skills, score_experience, score_language, "
                + "score_seniority, score_location, score_compensation, score_contract, classification, recommendation, strengths, gaps, reasons, method, created_at, updated_at");
        USER_TABLES.put("applications", "*");
        USER_TABLES.put("notifications", "*");
        USER_TABLES.put("ai_analyses", "id, prompt_key, provider, model, subject_type, subject_id, status, output, input_tokens, output_tokens, cost_usd, created_at");
        CHILD_QUERIES.put("resume_versions", "SELECT v.* FROM resume_versions v JOIN resumes r ON r.id = v.resume_id WHERE r.user_id = ?");
        CHILD_QUERIES.put("experience_skills", "SELECT es.* FROM experience_skills es JOIN experiences e ON e.id = es.experience_id WHERE e.user_id = ?");
        CHILD_QUERIES.put("project_skills", "SELECT ps.* FROM project_skills ps JOIN projects p ON p.id = ps.project_id WHERE p.user_id = ?");
        CHILD_QUERIES.put("application_events", "SELECT ev.* FROM application_events ev JOIN applications a ON a.id = ev.application_id WHERE a.user_id = ?");
        CHILD_QUERIES.put("audit_logs", "SELECT id, action, target_type, target_id, details, occurred_at FROM audit_logs WHERE actor_user_id = ?");
    }

    private final JdbcTemplate jdbc;
    private final UserService users;
    private final StoredFileRepository files;
    private final FileStorageService storage;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public PrivacyService(JdbcTemplate jdbc, UserService users, StoredFileRepository files, FileStorageService storage,
                          PasswordEncoder passwordEncoder, AuditService audit) {
        this.jdbc = jdbc;
        this.users = users;
        this.files = files;
        this.storage = storage;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public byte[] export(long userId) {
        User u = users.get(userId);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes)) {
            Map<String, Object> account = new LinkedHashMap<>();
            account.put("id", u.getId());
            account.put("email", u.getEmail());
            account.put("fullName", u.getFullName());
            account.put("role", u.getRole());
            account.put("locale", u.getLocale());
            account.put("timezone", u.getTimezone());
            account.put("createdAt", u.getCreatedAt());
            put(zip, "account.json", Json.write(account));
            for (var e : USER_TABLES.entrySet()) {
                put(zip, "data/" + e.getKey() + ".json", Json.write(rows("SELECT " + e.getValue() + " FROM " + e.getKey() + " WHERE user_id = ?", userId)));
            }
            for (var e : CHILD_QUERIES.entrySet()) {
                put(zip, "data/" + e.getKey() + ".json", Json.write(rows(e.getValue(), userId)));
            }
            for (StoredFile f : files.findByUserId(userId)) {
                try {
                    zip.putNextEntry(new ZipEntry("files/" + f.getId() + "-" + FileStorageService.sanitizeName(f.getOriginalName())));
                    zip.write(storage.read(f));
                    zip.closeEntry();
                } catch (ApiException missing) {
                    // file content missing on disk: metadata is still in data/stored_files.json
                }
            }
            put(zip, "README.txt", "Exportación de JobHunter AI.\ndata/*.json: tus datos por tabla. files/: tus archivos (CV subidos y PDFs generados).\n"
                    + "No incluye tu contraseña ni credenciales de integraciones.\n");
            zip.finish();
            audit.record(userId, "DATA_EXPORTED", "USER", userId, null);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Export failed", e);
        }
    }

    /** Irreversible: deletes the account (DB cascades) and the user's files; anonymizes logs. */
    @Transactional
    public void deleteAccount(long userId, String password) {
        User u = users.get(userId);
        if (password == null || !passwordEncoder.matches(password, u.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CREDENTIALS", "La contraseña no es correcta");
        }
        for (StoredFile f : files.findByUserId(userId)) {
            storage.delete(f);
        }
        jdbc.update("UPDATE audit_logs SET actor_user_id = NULL WHERE actor_user_id = ?", userId);
        jdbc.update("UPDATE api_usage_logs SET user_id = NULL WHERE user_id = ?", userId);
        jdbc.update("UPDATE job_source_executions SET user_id = NULL WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
        // the ACCOUNT_DELETED audit entry is written by the caller AFTER commit (a separate transaction inserting into
        // audit_logs here would wait on the rows this transaction just updated)
    }

    public void auditDeleted() {
        audit.record(null, "ACCOUNT_DELETED", "USER", null, null);
    }

    private List<Map<String, Object>> rows(String sql, long userId) {
        return jdbc.queryForList(sql, userId).stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            r.forEach((k, v) -> m.put(k, v instanceof byte[] b ? java.util.HexFormat.of().formatHex(b) : v));
            return m;
        }).toList();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
