package com.jobhunter.searches;

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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end discovery against local stubs (TEST DATA) and the real local MySQL test schema. */
class JobsIT extends IntegrationTest {

    static final StubServer STUB;

    static {
        try {
            STUB = new StubServer();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        STUB.on("/api/remote-jobs", Reply.json("""
                {"jobs":[{"id":1,"url":"https://example.test/remotive/1","title":"Senior Java Developer","company_name":"TEST DATA Acme Inc.",
                 "job_type":"full_time","publication_date":"2026-11-24T10:31:32","candidate_required_location":"LATAM",
                 "salary":"$4,000 / month","description":"<p>Java, Spring Boot and MySQL. Fluent English required.</p>"},
                {"id":2,"url":"https://example.test/remotive/2","title":"Marketing Manager","company_name":"TEST DATA Other",
                 "job_type":"full_time","publication_date":"2026-11-24T10:31:32","candidate_required_location":"USA",
                 "salary":"","description":"<p>Campaigns</p>"}]}"""));
        STUB.on("/jobs/api/search", Reply.json("""
                {"jobs":[{"title":"Sr. Java Developer","companyName":"TEST DATA Acme","employmentType":"Full Time",
                 "locationRestrictions":["Bolivia","Peru"],"timezoneRestrictions":[],
                 "description":"<p>Java, Spring Boot and MySQL. Fluent English required.</p>","pubDate":1794317072,
                 "applicationLink":"https://example.test/remotive/1?utm_source=himalayas","guid":"https://example.test/him/1"},
                {"title":"Laravel Developer","companyName":"TEST DATA Beta","employmentType":"Contract",
                 "locationRestrictions":["Bolivia"],"description":"<p>PHP and Laravel freelance contractor</p>","pubDate":1794317072,
                 "applicationLink":"https://example.test/him/2/apply","guid":"https://example.test/him/2"}]}"""));
    }

    @DynamicPropertySource
    static void connectors(DynamicPropertyRegistry r) {
        r.add("jobhunter.connectors.allow-private-network", () -> "true");
        for (String name : List.of("remotive", "himalayas", "jobicy", "remoteok", "greenhouse", "lever", "ashby", "serpapi", "adzuna", "apify")) {
            r.add("jobhunter.connectors.base-urls." + name, STUB::baseUrl);
        }
    }

    @AfterAll
    static void stop() {
        STUB.close();
    }

    @Autowired
    JobIngestionService ingestion;

    @Autowired
    JobSourceRepository sources;

    @Test
    void ingestionDeduplicatesByUrlAndKeepsEverySourceReference() {
        var remotive = sources.findByCode("remotive").orElseThrow();
        var himalayas = sources.findByCode("himalayas").orElseThrow();
        JobOfferDraft a = JobOfferDraft.builder("remotive", "1").sourceUrl("https://example.test/jobs/77?utm_source=x")
                .title("Java Developer").company("TEST DATA Dup Co").descriptionHtml("<p>Java role</p>").build();
        JobOfferDraft b = JobOfferDraft.builder("himalayas", "h-77").sourceUrl("https://www.example.test/jobs/77")
                .title("Java Developer").company("TEST DATA Dup Co").descriptionHtml("<p>Java role</p>")
                .allowedLocations(List.of("Bolivia")).build();
        var r1 = ingestion.ingest(remotive, List.of(a));
        var r2 = ingestion.ingest(himalayas, List.of(b));
        assertThat(r1.items().get(0).outcome()).isEqualTo(JobIngestionService.Outcome.NEW);
        assertThat(r2.items().get(0).outcome()).isEqualTo(JobIngestionService.Outcome.MERGED_DUPLICATE);
        assertThat(r2.items().get(0).offerId()).isEqualTo(r1.items().get(0).offerId());
        Long refs = jdbc.queryForObject("SELECT COUNT(*) FROM job_source_references WHERE job_offer_id = ?", Long.class,
                r1.items().get(0).offerId());
        assertThat(refs).isEqualTo(2);
        // re-ingesting the same item is an update, not a new offer
        var r3 = ingestion.ingest(remotive, List.of(a));
        assertThat(r3.items().get(0).outcome()).isEqualTo(JobIngestionService.Outcome.UNCHANGED);
    }

    @Test
    void savedSearchRunsEndToEndAndDetectsNewJobs() throws Exception {
        String token = register("search@test.example");
        Map<String, Object> request = Map.of("name", "Java remoto LATAM", "keywords", "java",
                "filters", Map.of("eligibility", List.of("ELIGIBLE", "LIKELY")),
                "sourceCodes", List.of("remotive", "himalayas"), "scheduleType", "MANUAL", "enabled", true,
                "maxResultsPerSource", 20, "notifyOnNew", true);
        String created = mvc.perform(post("/api/v1/searches").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long searchId = Json.tree(created).get("id").asLong();

        JsonNode exec = runAndWait(token, searchId);
        assertThat(exec.get("status").asString()).isEqualTo("SUCCEEDED");
        // Remotive #1 and Himalayas #1 are the same vacancy (apply link → same canonical URL as Remotive #1)
        assertThat(exec.get("jobsFound").asInt()).isEqualTo(1);
        assertThat(exec.get("jobsNew").asInt()).isEqualTo(1);
        assertThat(exec.get("sources").size()).isEqualTo(2);

        long execId = exec.get("id").asLong();
        String jobs = mvc.perform(get("/api/v1/searches/executions/" + execId + "/jobs?onlyNew=true").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode job = Json.tree(jobs).get("content").get(0);
        assertThat(job.get("boliviaEligibility").asString()).isEqualTo("ELIGIBLE");
        assertThat(job.get("sources").size()).isEqualTo(2);
        assertThat(job.get("skills").toString()).contains("Java").contains("Spring Boot");

        // second run: Remotive is inside its minimum interval → CACHED, and nothing is new anymore
        JsonNode second = runAndWait(token, searchId);
        assertThat(second.get("jobsNew").asInt()).isZero();
        assertThat(second.get("sources").toString()).contains("CACHED");

        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void catalogFiltersAndJobDetailWithAttribution() throws Exception {
        String token = register("filters@test.example");
        var himalayas = sources.findByCode("himalayas").orElseThrow();
        ingestion.ingest(himalayas, List.of(
                JobOfferDraft.builder("himalayas", "f1").sourceUrl("https://example.test/f/1").title("Java Engineer")
                        .company("TEST DATA F1").descriptionHtml("<p>Java and Spring Boot</p>")
                        .allowedLocations(List.of("Bolivia")).publishedAt(Instant.now(), false).build(),
                JobOfferDraft.builder("himalayas", "f2").sourceUrl("https://example.test/f/2").title("PHP Engineer")
                        .company("TEST DATA F2").descriptionHtml("<p>Laravel</p>")
                        .allowedLocations(List.of("Germany")).publishedAt(Instant.now(), false).build(),
                JobOfferDraft.builder("himalayas", "f3").sourceUrl("https://example.test/f/3").title("Frontend\n  Engineer")
                        .company("TEST DATA F3").descriptionHtml("<p>JavaScript and TypeScript</p>")
                        .allowedLocations(List.of("Germany")).publishedAt(Instant.now(), false).build()));
        // keywords match whole words: "java" must not match "JavaScript"; titles are whitespace-normalised
        mvc.perform(get("/api/v1/jobs?q=java").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].companyName").value("TEST DATA F1"));
        mvc.perform(get("/api/v1/jobs?q=javascript").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.content[0].title").value("Frontend Engineer"));
        long java = jdbc.queryForObject("SELECT id FROM skills WHERE normalized_name='java'", Long.class);
        String page = mvc.perform(get("/api/v1/jobs?eligibility=ELIGIBLE&skillIds=" + java).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andReturn().getResponse().getContentAsString();
        long id = Json.tree(page).get("content").get(0).get("id").asLong();
        mvc.perform(get("/api/v1/jobs?q=laravel").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.content[0].companyName").value("TEST DATA F2"));
        mvc.perform(get("/api/v1/jobs?countries=BO").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/jobs/" + id).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.references[0].attributionText").value("Vía Himalayas"))
                .andExpect(jsonPath("$.eligibilityEvidence").exists())
                // flat contract: summary fields live at the top level (no nested "summary" object)
                .andExpect(jsonPath("$.title").value("Java Engineer"))
                .andExpect(jsonPath("$.skills").isArray())
                .andExpect(jsonPath("$.summary").doesNotExist());
        mvc.perform(put("/api/v1/jobs/" + id + "/state").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("hidden", true))))
                .andExpect(jsonPath("$.hidden").value(true));
        mvc.perform(get("/api/v1/jobs?eligibility=ELIGIBLE").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void watchlistValidatesTokens() throws Exception {
        String token = register("watch@test.example");
        mvc.perform(post("/api/v1/watchlist").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("ats", "GREENHOUSE", "boardToken", "../../etc/passwd"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mvc.perform(post("/api/v1/watchlist").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("ats", "LEVER", "boardToken", "testco", "companyName", "TEST DATA Co"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.ats").value("LEVER"));
    }

    private JsonNode runAndWait(String token, long searchId) throws Exception {
        String started = mvc.perform(post("/api/v1/searches/" + searchId + "/run").header("Authorization", bearer(token)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        long execId = Json.tree(started).get("executionId").asLong();
        for (int i = 0; i < 100; i++) {
            JsonNode e = Json.tree(mvc.perform(get("/api/v1/searches/executions/" + execId).header("Authorization", bearer(token)))
                    .andReturn().getResponse().getContentAsString());
            if (!"RUNNING".equals(e.get("status").asString())) return e;
            Thread.sleep(100);
        }
        throw new AssertionError("search did not finish");
    }
}
