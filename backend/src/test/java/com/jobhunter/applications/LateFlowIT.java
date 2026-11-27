package com.jobhunter.applications;

import com.jobhunter.jobs.application.JobIngestionService;
import com.jobhunter.jobs.domain.JobOfferDraft;
import com.jobhunter.jobs.infrastructure.JobSourceRepository;
import com.jobhunter.shared.json.Json;
import com.jobhunter.support.IntegrationTest;
import com.jobhunter.support.StubServer;
import com.jobhunter.support.StubServer.Reply;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phases 5–7 end to end (optimizer, letters, applications, analytics, data rights). Stubbed DeepSeek, TEST DATA only. */
class LateFlowIT extends IntegrationTest {

    static final StubServer STUB;

    static {
        try {
            STUB = new StubServer();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("jobhunter.connectors.allow-private-network", () -> "true");
        r.add("jobhunter.connectors.base-urls.deepseek", STUB::baseUrl);
    }

    @AfterAll
    static void stop() {
        STUB.close();
    }

    @Autowired
    JobIngestionService ingestion;
    @Autowired
    JobSourceRepository sources;

    static String completion(Object output) {
        return Json.write(Map.of("model", "deepseek-flash", "choices", List.of(Map.of("finish_reason", "stop",
                "message", Map.of("role", "assistant", "content", Json.write(output)))), "usage",
                Map.of("prompt_tokens", 500, "completion_tokens", 100)));
    }

    static Map<String, Object> analysis() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("summary", "Backend Java (TEST DATA)");
        m.put("seniority", "MID");
        m.put("minYearsExperience", null);
        m.put("mandatorySkills", List.of(Map.of("name", "Java", "minYears", 1)));
        m.put("preferredSkills", List.of());
        m.put("languages", List.of());
        Map<String, Object> salary = new LinkedHashMap<>();
        salary.put("min", null);
        salary.put("max", null);
        salary.put("currency", null);
        salary.put("period", null);
        m.put("salary", salary);
        Map<String, Object> loc = new LinkedHashMap<>();
        loc.put("scope", "LATAM");
        loc.put("countries", List.of());
        loc.put("boliviaEligibility", "UNKNOWN");
        loc.put("evidenceQuote", null);
        m.put("location", loc);
        m.put("engagementType", "UNKNOWN");
        m.put("contractType", "UNKNOWN");
        m.put("injectionSuspected", false);
        return m;
    }

    @Test
    void optimizerLettersApplicationsAnalyticsAndDataRights() throws Exception {
        String token = register("late@test.example");
        mvc.perform(put("/api/v1/admin/integrations/DEEPSEEK/secret").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("apiKey", "sk-test-0000000000")))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/integrations/DEEPSEEK").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("enabled", true)))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/me/consents").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(List.of(Map.of("consentType", "AI_PROCESS_PROFILE", "provider", "deepseek", "granted", true)))))
                .andExpect(status().isOk());

        long java = jdbc.queryForObject("SELECT id FROM skills WHERE normalized_name='java'", Long.class);
        mvc.perform(post("/api/v1/profiles").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Java", "headline", "Backend Developer", "summary", "Backend con Java.")))).andExpect(status().isCreated());
        String exp = mvc.perform(post("/api/v1/experiences").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("companyName", "TEST DATA Acme", "positionTitle", "Backend Developer", "employmentType", "FULL_TIME",
                        "startDate", "2022-01-01", "current", true, "achievements", List.of("Reduje la latencia un 30%"),
                        "skillIds", List.of(java))))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long expId = Json.tree(exp).get("id").asLong();
        String created = mvc.perform(post("/api/v1/resumes").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", "CV base", "language", "es", "templateId", "classic")))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long resumeId = Json.tree(created).get("resume").get("id").asLong();
        long jobId = ingestion.ingest(sources.findByCode("greenhouse").orElseThrow(), List.of(JobOfferDraft.builder("greenhouse", "late:1")
                .sourceUrl("https://example.test/late/1").title("Java Developer").company("TEST DATA Hiring Co").workplaceType("remote")
                .location("Remote - LATAM").descriptionHtml("<p>Java developer, remote in LATAM.</p>").build())).items().get(0).offerId();

        // --- Phase 5: optimizer (analysis + CV_OPTIMIZE calls) ---
        Map<String, Object> optimize = new LinkedHashMap<>();
        optimize.put("headline", "Java Backend Developer");
        optimize.put("summary", Map.of("text", "Backend developer working with Java.", "sourceRefs", List.of("skill:" + java)));
        optimize.put("experiences", List.of(Map.of("ref", "exp:" + expId, "bullets", List.of(
                Map.of("text", "Reduced latency by 30% in Java services", "sourceRefs", List.of("ach:" + expId + ":0", "skill:" + java)),
                Map.of("text", "Managed a budget of 2 million", "sourceRefs", List.of("exp:" + expId))))));
        optimize.put("projects", List.of());
        optimize.put("skillOrder", List.of("skill:" + java));
        optimize.put("injectionSuspected", false);
        Map<String, Object> letter = new LinkedHashMap<>();
        letter.put("subject", "Application: Java Developer");
        letter.put("greeting", "Dear team,");
        letter.put("paragraphs", List.of(Map.of("text", "I work as a backend developer with Java.", "sourceRefs", List.of("exp:" + expId)),
                Map.of("text", "I have 9 years of experience with Kubernetes.", "sourceRefs", List.of())));
        letter.put("closing", "Kind regards,");
        letter.put("injectionSuspected", false);
        STUB.on("/chat/completions", Reply.json(completion(analysis())), Reply.json(completion(optimize)), Reply.json(completion(letter)));

        mvc.perform(post("/api/v1/jobs/" + jobId + "/optimize-resume").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("resumeId", resumeId, "language", "en"))))
                .andExpect(status().isAccepted());
        JsonNode opt = waitFor("/api/v1/jobs/" + jobId + "/optimize-resume?resumeId=" + resumeId, token);
        assertThat(opt.path("task").path("state").asString()).isEqualTo("SUCCEEDED");
        int versionNo = opt.path("version").path("versionNo").asInt();
        assertThat(opt.path("version").path("origin").asString()).isEqualTo("OPTIMIZED");
        String v = mvc.perform(get("/api/v1/resumes/" + resumeId + "/versions/" + versionNo).header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        JsonNode bullets = Json.tree(v).path("content").path("experience").get(0).path("bullets");
        assertThat(bullets.get(0).path("status").asString()).isEqualTo("GROUNDED");
        assertThat(bullets.get(1).path("status").asString()).isEqualTo("SUGGESTED");
        assertThat(Json.tree(v).path("validationReport").path("suggestedCount").asInt()).isEqualTo(1);
        mvc.perform(post("/api/v1/resumes/" + resumeId + "/versions/" + versionNo + "/approve").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNCONFIRMED_SUGGESTIONS"));

        // --- Phase 5: cover letter ---
        mvc.perform(post("/api/v1/jobs/" + jobId + "/cover-letters").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("format", "EMAIL_INTRO", "language", "en"))))
                .andExpect(status().isAccepted());
        waitTask("/api/v1/jobs/" + jobId + "/cover-letters/task", token);
        String list = mvc.perform(get("/api/v1/cover-letters?jobId=" + jobId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1)).andReturn().getResponse().getContentAsString();
        long letterId = Json.tree(list).path("content").get(0).path("id").asLong();
        String l = mvc.perform(get("/api/v1/cover-letters/" + letterId).header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        assertThat(Json.tree(l).path("suggestions").size()).isEqualTo(1);
        assertThat(Json.tree(l).path("body").asString()).endsWith("TEST DATA User");
        mvc.perform(post("/api/v1/cover-letters/" + letterId + "/approve").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        String edited = mvc.perform(put("/api/v1/cover-letters/" + letterId).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("subject", "Application", "body", "Dear team,\n\nI work with Java."))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionNo").value(2)).andReturn().getResponse().getContentAsString();
        long letter2 = Json.tree(edited).path("id").asLong();
        mvc.perform(post("/api/v1/cover-letters/" + letter2 + "/approve").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.status").value("APPROVED"));
        byte[] pdf = mvc.perform(get("/api/v1/cover-letters/" + letter2 + "/pdf").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        // --- Phase 6: applications ---
        String app = mvc.perform(post("/api/v1/applications").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("jobOfferId", jobId)))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long appId = Json.tree(app).path("id").asLong();
        mvc.perform(post("/api/v1/applications").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("jobOfferId", jobId)))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPLICATION_EXISTS"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/applications/" + appId)
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("resumeId", resumeId, "resumeVersionNo", 1, "coverLetterId", letter2, "method", "EMAIL"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/applications/" + appId + "/prepare").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email.subject").value("Application"))
                .andExpect(jsonPath("$.coverLetter.id").value(letter2));
        mvc.perform(post("/api/v1/applications/" + appId + "/transition").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("toStatus", "APPLIED"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        mvc.perform(post("/api/v1/applications/" + appId + "/mark-submitted").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("method", "EMAIL"))))
                .andExpect(jsonPath("$.status").value("APPLIED")).andExpect(jsonPath("$.followUpAt").exists());
        mvc.perform(post("/api/v1/applications/" + appId + "/transition").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("toStatus", "INTERVIEW", "note", "Entrevista técnica"))))
                .andExpect(jsonPath("$.status").value("INTERVIEW"));
        mvc.perform(get("/api/v1/applications/" + appId + "/events").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].toStatus").value("INTERVIEW"));
        mvc.perform(get("/api/v1/applications/board").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.columns.length()").value(11));

        // --- Phase 7: analytics ---
        mvc.perform(get("/api/v1/analytics/overview").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.applications.applied").value(1))
                .andExpect(jsonPath("$.applications.interviews").value(1)).andExpect(jsonPath("$.applications.responseRate").value(1.0));
        mvc.perform(get("/api/v1/analytics/applications/funnel").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[?(@.stage=='APPLIED')].count").value(1));
        mvc.perform(get("/api/v1/analytics/jobs/breakdown?dimension=technology").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].label").value("Java"));
        mvc.perform(get("/api/v1/analytics/jobs/breakdown?dimension=salaryBand").header("Authorization", bearer(token))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/analytics/jobs/timeline").header("Authorization", bearer(token))).andExpect(status().isOk());
        String demand = mvc.perform(get("/api/v1/analytics/skills/demand").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(Json.tree(demand).get(0).path("userEvidence").asString()).as(demand).isEqualTo("PROFESSIONAL");
        mvc.perform(get("/api/v1/analytics/usage/daily").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].code").value("DEEPSEEK"));

        // --- data rights ---
        byte[] zip = mvc.perform(get("/api/v1/me/export").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        List<String> entries = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var e = in.getNextEntry(); e != null; e = in.getNextEntry()) entries.add(e.getName());
        }
        assertThat(entries).contains("account.json", "data/applications.json", "data/resume_versions.json", "data/cover_letters.json");
        mvc.perform(delete("/api/v1/me").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("password", "wrong-password")))).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/me").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("password", "test-password-123")))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email='late@test.example'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stored_files", Long.class)).isZero();
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "late@test.example", "password", "test-password-123")))).andExpect(status().isUnauthorized());
    }

    private JsonNode waitFor(String url, String token) throws Exception {
        for (int i = 0; i < 150; i++) {
            JsonNode n = Json.tree(mvc.perform(get(url).header("Authorization", bearer(token))).andReturn().getResponse().getContentAsString());
            if (!"RUNNING".equals(n.path("task").path("state").asString())) return n;
            Thread.sleep(100);
        }
        throw new AssertionError("task did not finish");
    }

    private JsonNode waitTask(String url, String token) throws Exception {
        for (int i = 0; i < 150; i++) {
            JsonNode n = Json.tree(mvc.perform(get(url).header("Authorization", bearer(token))).andReturn().getResponse().getContentAsString());
            if (!"RUNNING".equals(n.path("state").asString())) {
                assertThat(n.path("state").asString()).as(n.toString()).isEqualTo("SUCCEEDED");
                return n;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("task did not finish");
    }
}
