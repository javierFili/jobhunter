package com.jobhunter.ai.application;

import com.jobhunter.ai.domain.PromptVersion;
import com.jobhunter.ai.infrastructure.PromptVersionRepository;
import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Versioned prompts. Defaults live in {@code resources/prompts/<KEY>.json}; on startup a default whose checksum is not
 * stored yet becomes a new version (and active only when no version was active). Admin edits create new versions.
 */
@Service
public class PromptRegistry {

    private static final Logger log = LoggerFactory.getLogger(PromptRegistry.class);
    public static final List<String> KEYS = List.of("JOB_ANALYSIS", "MATCH_SEMANTIC", "CV_PARSE", "CV_OPTIMIZE",
            "COVER_LETTER", "SKILL_RECOMMENDATION");
    private static final Pattern VAR = Pattern.compile("\\{\\{(\\w+)}}");

    private final PromptVersionRepository repository;
    private final boolean seedOnStartup;
    private final org.springframework.transaction.support.TransactionTemplate tx;

    public PromptRegistry(PromptVersionRepository repository,
                          @org.springframework.beans.factory.annotation.Value("${jobhunter.ai.seed-prompts:true}") boolean seedOnStartup,
                          org.springframework.transaction.PlatformTransactionManager txManager) {
        this.repository = repository;
        this.seedOnStartup = seedOnStartup;
        this.tx = new org.springframework.transaction.support.TransactionTemplate(txManager);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (seedOnStartup) tx.executeWithoutResult(s -> seedDefaults());
    }

    void seedDefaults() {
        for (String key : KEYS) {
            try (InputStream in = new ClassPathResource("prompts/" + key + ".json").getInputStream()) {
                JsonNode def = Json.tree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                String system = def.path("system").asString();
                String user = def.path("user").asString();
                String schema = def.path("schema").toString();
                BigDecimal temperature = def.has("temperature") ? def.path("temperature").decimalValue() : null;
                String checksum = checksum(system, user, schema, temperature);
                if (repository.findByPromptKeyAndChecksum(key, checksum).isEmpty()) {
                    boolean noneActive = repository.findByPromptKeyAndActiveTrue(key).isEmpty();
                    PromptVersion v = new PromptVersion(key, repository.maxVersion(key) + 1, system, user, schema,
                            temperature, checksum, def.path("notes").asString("Versión por defecto"), null);
                    v.setActive(noneActive || def.path("activateOnUpgrade").asBoolean(true));
                    if (v.isActive()) repository.findByPromptKeyAndActiveTrue(key).ifPresent(old -> old.setActive(false));
                    repository.save(v);
                    log.info("Seeded prompt {} v{}", key, v.getVersion());
                }
            } catch (IOException e) {
                throw new IllegalStateException("Missing default prompt " + key, e);
            }
        }
    }

    @Transactional(readOnly = true)
    public PromptVersion active(String key) {
        return repository.findByPromptKeyAndActiveTrue(key)
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.CONFLICT, "PROMPT_MISSING",
                        "No hay una versión activa del prompt " + key));
    }

    @Transactional(readOnly = true)
    public List<PromptVersion> versions(String key) {
        return repository.findByPromptKeyOrderByVersionDesc(key);
    }

    @Transactional
    public PromptVersion createVersion(long actorId, String key, String system, String user, String schemaJson,
                                       BigDecimal temperature, String notes) {
        if (!KEYS.contains(key)) throw ApiException.notFound("Prompt");
        if (!Json.tree(schemaJson).isObject()) throw ApiException.badRequest("VALIDATION_FAILED", "El esquema debe ser un objeto JSON");
        if (!system.toLowerCase().contains("json") && !user.toLowerCase().contains("json")) {
            throw ApiException.badRequest("VALIDATION_FAILED", "El prompt debe mencionar la palabra 'json' (requisito de DeepSeek)");
        }
        String checksum = checksum(system, user, Json.tree(schemaJson).toString(), temperature);
        return repository.save(new PromptVersion(key, repository.maxVersion(key) + 1, system, user,
                Json.tree(schemaJson).toString(), temperature, checksum, notes, actorId));
    }

    @Transactional
    public PromptVersion activate(String key, int version) {
        PromptVersion v = repository.findByPromptKeyAndVersion(key, version).orElseThrow(() -> ApiException.notFound("Versión"));
        repository.findByPromptKeyAndActiveTrue(key).ifPresent(old -> old.setActive(false));
        repository.flush();
        v.setActive(true);
        return v;
    }

    /** Minimal, safe renderer: only {{name}} substitution, no expressions. Unknown variables become empty. */
    public static String render(String template, Map<String, String> vars) {
        Matcher m = VAR.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(vars.getOrDefault(m.group(1), "")));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static String checksum(String system, String user, String schema, BigDecimal temperature) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest((system + "\u0000" + user + "\u0000" + schema + "\u0000"
                    + temperature).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
