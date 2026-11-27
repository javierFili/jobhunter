package com.jobhunter.jobsources;

import com.jobhunter.jobs.domain.JobOfferDraft;
import com.jobhunter.jobsources.domain.FetchResult;
import com.jobhunter.jobsources.domain.SourceQuery;
import com.jobhunter.jobsources.infrastructure.ConnectorProperties;
import com.jobhunter.jobsources.infrastructure.FreelancerConnector;
import com.jobhunter.jobsources.infrastructure.GetOnBoardConnector;
import com.jobhunter.jobsources.infrastructure.LarajobsConnector;
import com.jobhunter.jobsources.infrastructure.WeWorkRemotelyConnector;
import com.jobhunter.shared.http.SafeHttpClient;
import com.jobhunter.support.StubServer;
import com.jobhunter.support.StubServer.Reply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Get on Board, We Work Remotely, LaraJobs and Freelancer.com against local stubs (SYNTHETIC data, real shapes). */
class NewSourcesConnectorsTest {

    private StubServer stub;
    private ConnectorProperties props;
    private final SafeHttpClient http = new SafeHttpClient();

    @BeforeEach
    void setUp() throws Exception {
        stub = new StubServer();
        String base = stub.baseUrl();
        props = new ConnectorProperties(Map.of("getonboard", base, "weworkremotely", base, "larajobs", base, "freelancer", base), true);
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    private static SourceQuery query(String keywords) {
        return new SourceQuery(keywords, List.of(), true, null, 50, null, null);
    }

    private static Reply xml(String body) {
        return new Reply(200, body, Map.of("Content-Type", "application/rss+xml"));
    }

    @Test
    void getOnBoardMapsRemoteLocalCountriesSalaryAndSeniority() {
        stub.on("/api/v0/search/jobs", Reply.json("""
                {"data":[{"id":"java-dev-test-data","type":"job","attributes":{"title":"Desarrollador Java",
                  "description":"<p>Java y Spring Boot</p>","functions":"<p>APIs REST</p>","desirable":null,"benefits":null,
                  "remote":true,"remote_modality":"remote_local","countries":["Bolivia","Chile"],"min_salary":1500,"max_salary":2000,
                  "seniority":{"data":{"id":2,"type":"seniority"}},"modality":{"data":{"id":1,"type":"modality"}},
                  "published_at":1793133968,
                  "company":{"data":{"id":"1","type":"company","attributes":{"name":"TEST DATA GOB Co"}}}},
                  "links":{"public_url":"https://example.test/gob/1"}}],"meta":{"page":1,"per_page":1,"total_pages":1}}"""));
        FetchResult r = new GetOnBoardConnector(http, props).fetch(query("java"), Map.of());
        JobOfferDraft d = r.items().get(0);
        assertThat(d.companyName()).isEqualTo("TEST DATA GOB Co");
        assertThat(d.allowedLocations()).containsExactly("Bolivia", "Chile");
        assertThat(d.remote()).isTrue();
        assertThat(d.salaryMin()).isEqualByComparingTo(new BigDecimal("1500"));
        assertThat(d.salaryPeriod()).isEqualTo("MONTH");
        assertThat(d.seniority()).isEqualTo("junior");
        assertThat(d.employmentType()).isEqualTo("full time");
        assertThat(d.descriptionHtml()).contains("Spring Boot").contains("APIs REST");
        assertThat(stub.requests.get(0)).contains("query=java");
    }

    @Test
    void weWorkRemotelySplitsCompanyFromTitleAndDedupesAcrossFeeds() {
        String feed = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><item>
                  <title>TEST DATA WWR Co: Senior Java Engineer -</title><region>Latin America Only</region>
                  <type>Full-Time</type><description>&lt;p&gt;Java&lt;/p&gt;</description>
                  <pubDate>Wed, 25 Nov 2026 18:32:27 +0000</pubDate>
                  <guid>https://example.test/wwr/1</guid><link>https://example.test/wwr/1</link>
                </item></channel></rss>""";
        WeWorkRemotelyConnector c = new WeWorkRemotelyConnector(http, props);
        stub.on("/categories/remote-back-end-programming-jobs.rss", xml(feed));
        stub.on("/categories/remote-full-stack-programming-jobs.rss", xml(feed));
        FetchResult r = c.fetch(query(null), Map.of());
        assertThat(r.items()).hasSize(1);
        assertThat(r.items().get(0).title()).isEqualTo("Senior Java Engineer");
        assertThat(r.items().get(0).companyName()).isEqualTo("TEST DATA WWR Co");
        assertThat(r.items().get(0).allowedLocations()).containsExactly("Latin America Only");
        assertThat(r.items().get(0).publishedAt()).isNotNull();
        // raw payload goes to a MySQL JSON column: must be valid JSON
        assertThat(com.jobhunter.shared.json.Json.tree(r.items().get(0).rawPayload()).path("region").asString())
                .isEqualTo("Latin America Only");
    }

    @Test
    void larajobsBuildsADescriptionFromStructuredFields() {
        stub.on("/feed", xml("""
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0" xmlns:larajobs="https://larajobs.com" xmlns:dc="http://purl.org/dc/elements/1.1/"><channel><item>
                  <title>Laravel Developer</title><link>https://example.test/lj/1</link>
                  <pubDate>Mon, 16 Nov 2026 16:51:21 +0000</pubDate><dc:creator>TEST DATA Fish &amp;amp; Chips</dc:creator>
                  <larajobs:location>Remote</larajobs:location><larajobs:job_type>FULL_TIME</larajobs:job_type>
                  <larajobs:salary>$70,000-120,000</larajobs:salary><larajobs:company>TEST DATA Fish &amp;amp; Chips</larajobs:company>
                  <larajobs:tags>Laravel,MySQL,VueJS</larajobs:tags><guid>https://example.test/lj/1</guid>
                </item></channel></rss>"""));
        JobOfferDraft d = new LarajobsConnector(http, props).fetch(query(null), Map.of()).items().get(0);
        assertThat(d.companyName()).isEqualTo("TEST DATA Fish & Chips");
        assertThat(d.remote()).isTrue();
        assertThat(d.descriptionHtml()).contains("Laravel, MySQL, VueJS");
        assertThat(d.salaryText()).isEqualTo("$70,000-120,000");
        assertThat(com.jobhunter.shared.json.Json.tree(d.rawPayload()).isObject()).isTrue();
    }

    @Test
    void rssWithDoctypeIsRejected() {
        stub.on("/feed", xml("""
                <?xml version="1.0"?><!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]>
                <rss><channel><item><title>&x;</title></item></channel></rss>"""));
        assertThatThrownBy(() -> new LarajobsConnector(http, props).fetch(query(null), Map.of()))
                .isInstanceOf(SafeHttpClient.HttpCallException.class);
    }

    @Test
    void freelancerKeepsFixedBudgetsAsTextAndHourlyRatesPerHour() {
        stub.on("/api/projects/0.1/projects/active/", Reply.json("""
                {"status":"success","result":{"total_count":2,"projects":[
                  {"id":1,"title":"Spring Boot API","seo_url":"java/spring-boot-api","type":"fixed","description":"Build an API",
                   "budget":{"minimum":250.0,"maximum":750.0},"currency":{"code":"USD"},"time_submitted":1793133968,
                   "jobs":[{"name":"Java"},{"name":"Spring Boot"}]},
                  {"id":2,"title":"Laravel fixes","seo_url":"php/laravel-fixes","type":"hourly","description":"Fix bugs",
                   "budget":{"minimum":15.0,"maximum":25.0},"currency":{"code":"USD"},"time_submitted":1793133968,"jobs":[]}]}}"""));
        FetchResult r = new FreelancerConnector(http, props).fetch(query("java"), Map.of());
        JobOfferDraft fixed = r.items().get(0);
        assertThat(fixed.salaryMin()).isNull();
        // a fixed budget is not a salary: no salary text (the parser would turn it into a monthly figure)
        assertThat(fixed.salaryText()).isNull();
        assertThat(fixed.descriptionHtml()).contains("Presupuesto fijo USD 250 – 750");
        assertThat(fixed.employmentType()).isEqualTo("freelance");
        assertThat(fixed.sourceUrl()).endsWith("/projects/java/spring-boot-api");
        JobOfferDraft hourly = r.items().get(1);
        assertThat(hourly.salaryPeriod()).isEqualTo("HOUR");
        assertThat(hourly.salaryMin()).isEqualByComparingTo(new BigDecimal("15"));
    }
}
