package com.jobhunter.jobsources;

import com.jobhunter.jobs.domain.JobOfferDraft;
import com.jobhunter.jobsources.domain.FetchResult;
import com.jobhunter.jobsources.domain.SourceQuery;
import com.jobhunter.jobsources.infrastructure.AdzunaConnector;
import com.jobhunter.jobsources.infrastructure.AshbyConnector;
import com.jobhunter.jobsources.infrastructure.ConnectorProperties;
import com.jobhunter.jobsources.infrastructure.GreenhouseConnector;
import com.jobhunter.jobsources.infrastructure.HimalayasConnector;
import com.jobhunter.jobsources.infrastructure.JobicyConnector;
import com.jobhunter.jobsources.infrastructure.LeverConnector;
import com.jobhunter.jobsources.infrastructure.RemoteOkConnector;
import com.jobhunter.jobsources.infrastructure.RemotiveConnector;
import com.jobhunter.jobsources.infrastructure.SerpApiConnector;
import com.jobhunter.shared.http.SafeHttpClient;
import com.jobhunter.support.StubServer;
import com.jobhunter.support.StubServer.Reply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Connector mapping against local stubs. Fixtures are SYNTHETIC (TEST DATA) but follow the real response shapes. */
class ConnectorsTest {

    private StubServer stub;
    private ConnectorProperties props;
    private final SafeHttpClient http = new SafeHttpClient();

    @BeforeEach
    void setUp() throws Exception {
        stub = new StubServer();
        String base = stub.baseUrl();
        props = new ConnectorProperties(Map.of("remotive", base, "himalayas", base, "jobicy", base, "remoteok", base,
                "greenhouse", base, "lever", base, "ashby", base, "serpapi", base, "adzuna", base, "apify", base), true);
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    private static SourceQuery query(String keywords) {
        return new SourceQuery(keywords, List.of(), true, null, 50, null, null);
    }

    @Test
    void himalayas() {
        stub.on("/jobs/api/search", Reply.json("""
                {"comments":"TEST DATA","offset":0,"limit":20,"totalCount":1,"jobs":[{
                  "title":"Backend Developer (Java)","excerpt":"x","companyName":"TEST DATA Corp","employmentType":"Full Time",
                  "minSalary":60000,"maxSalary":80000,"salaryPeriod":"annual","seniority":["Mid-level"],"currency":"USD",
                  "locationRestrictions":["Argentina","Bolivia","Chile"],"timezoneRestrictions":[-5,-4,-3],
                  "description":"<p>Java and Spring Boot</p>","pubDate":1794317072,"expiryDate":1796908875,
                  "applicationLink":"https://example.test/apply/1","guid":"https://example.test/jobs/1"}]}"""));
        FetchResult r = new HimalayasConnector(http, props).fetch(query("java"), Map.of("apiKey", "TEST-KEY"));
        assertThat(r.items()).hasSize(1);
        JobOfferDraft d = r.items().get(0);
        assertThat(d.externalId()).isEqualTo("https://example.test/jobs/1");
        assertThat(d.allowedLocations()).contains("Bolivia");
        assertThat(d.timezoneOffsets()).containsExactly(-5, -4, -3);
        assertThat(d.salaryPeriod()).isEqualTo("YEAR");
        assertThat(d.applyUrl()).isEqualTo("https://example.test/apply/1");
        assertThat(stub.authHeaders).contains("Bearer TEST-KEY");
        assertThat(stub.requests.get(0)).contains("q=java");
    }

    @Test
    void remotiveMapsCountryListsOnlyWhenAllAreCountries() {
        stub.on("/api/remote-jobs", Reply.json("""
                {"0-legal-notice":"TEST DATA","job-count":2,"jobs":[
                 {"id":1,"url":"https://example.test/r/1","title":"Laravel Developer","company_name":"TEST DATA A",
                  "job_type":"contract","publication_date":"2026-11-24T10:31:32",
                  "candidate_required_location":"Americas, LATAM, USA timezones","salary":"$57K - $67K","description":"<p>PHP</p>"},
                 {"id":2,"url":"https://example.test/r/2","title":"Java Developer","company_name":"TEST DATA B",
                  "job_type":"full_time","publication_date":"2026-11-24T10:31:32",
                  "candidate_required_location":"USA, Canada","salary":"","description":"<p>Java</p>"}]}"""));
        FetchResult r = new RemotiveConnector(http, props).fetch(query(null), Map.of());
        assertThat(r.items()).hasSize(2);
        assertThat(r.items().get(0).allowedLocations()).isEmpty();
        assertThat(r.items().get(0).locationText()).contains("LATAM");
        assertThat(r.items().get(0).salaryText()).isEqualTo("$57K - $67K");
        assertThat(r.items().get(1).allowedLocations()).containsExactly("USA", "Canada");
    }

    @Test
    void jobicyAndRemoteOk() {
        stub.on("/api/v2/remote-jobs", Reply.json("""
                {"apiVersion":"2","jobCount":1,"jobs":[{"id":157144,"url":"https://example.test/j/1","jobTitle":"Backend Engineer, Java",
                 "companyName":"TEST DATA C","jobIndustry":["Software"],"jobType":["Full-Time"],"jobGeo":"LATAM, USA",
                 "jobLevel":"Any","jobDescription":"<p>Java</p>","pubDate":"2026-11-26T04:30:34+00:00"}]}"""));
        stub.on("/api", Reply.json("""
                [{"last_updated":1,"legal":"TEST DATA legal notice"},
                 {"id":"1137839","date":"2026-11-19T16:00:34+00:00","company":"TEST DATA D","position":"PHP Engineer",
                  "tags":["php"],"description":"<p>PHP</p>","location":"Worldwide","apply_url":"https://example.test/ok/apply",
                  "salary_min":0,"salary_max":0,"url":"https://example.test/ok/1"}]"""));
        FetchResult j = new JobicyConnector(http, props).fetch(query("java"), Map.of());
        assertThat(j.items().get(0).allowedLocations()).containsExactly("LATAM", "USA");
        assertThat(j.items().get(0).seniority()).isNull();
        FetchResult ok = new RemoteOkConnector(http, props).fetch(query(null), Map.of());
        assertThat(ok.items()).hasSize(1);
        assertThat(ok.items().get(0).salaryText()).isNull();
        assertThat(ok.items().get(0).locationText()).isEqualTo("Worldwide");
    }

    @Test
    void atsBoardsAreCompleteListingsWithCompanyPrefixedIds() {
        stub.on("/v1/boards/testco/jobs", Reply.json("""
                {"jobs":[{"absolute_url":"https://example.test/gh/1","id":11,"title":"Java Engineer","company_name":"TEST DATA GH",
                 "first_published":"2026-11-23T17:02:31-05:00","updated_at":"2026-11-23T17:02:31-05:00",
                 "location":{"name":"Remote - LATAM"},"content":"&lt;p&gt;Java&lt;/p&gt;"}],"meta":{"total":1}}"""));
        stub.on("/v0/postings/testco", Reply.json("""
                [{"id":"ad208490","text":"Backend Developer","categories":{"location":"Berlin, Germany","commitment":"Full Time"},
                  "country":"DE","workplaceType":"onsite","createdAt":1788199939703,"description":"<p>Kotlin</p>",
                  "lists":[{"text":"Requirements","content":"<li>Java</li>"}],
                  "salaryRange":{"min":60000,"max":80000,"currency":"EUR","interval":"per-year-salary"},
                  "hostedUrl":"https://example.test/lever/1","applyUrl":"https://example.test/lever/1/apply"}]"""));
        stub.on("/posting-api/job-board/testco", Reply.json("""
                {"apiVersion":"1","jobs":[
                 {"id":"7458","title":"Engineer - EU","employmentType":"FullTime","location":"Remote - European Union",
                  "secondaryLocations":[{"location":"Spain"}],"isListed":true,"isRemote":true,"workplaceType":"Remote",
                  "publishedAt":"2024-03-04T14:29:08.532+00:00","jobUrl":"https://example.test/ashby/1","applyUrl":"https://example.test/ashby/1/apply",
                  "descriptionHtml":"<p>Go</p>","descriptionPlain":"Go",
                  "compensation":{"summaryComponents":[{"compensationType":"Salary","interval":"1 YEAR","currencyCode":"EUR","minValue":150000,"maxValue":250000}]}},
                 {"id":"hidden","title":"Unlisted","isListed":false,"jobUrl":"https://example.test/ashby/2"}]}"""));
        FetchResult gh = new GreenhouseConnector(http, props).fetch(SourceQuery.board("testco", null, 100), Map.of());
        assertThat(gh.completeListing()).isTrue();
        assertThat(gh.listingPrefix()).isEqualTo("testco:");
        assertThat(gh.items().get(0).externalId()).isEqualTo("testco:11");
        FetchResult lv = new LeverConnector(http, props).fetch(SourceQuery.board("testco", "TEST DATA Lever Co", 100), Map.of());
        assertThat(lv.items().get(0).countryHint()).isEqualTo("DE");
        assertThat(lv.items().get(0).salaryPeriod()).isEqualTo("YEAR");
        assertThat(lv.items().get(0).descriptionHtml()).contains("Requirements");
        FetchResult ab = new AshbyConnector(http, props).fetch(SourceQuery.board("testco", null, 100), Map.of());
        assertThat(ab.items()).hasSize(1);
        assertThat(ab.items().get(0).salaryMin()).isEqualByComparingTo("150000");
    }

    @Test
    void serpApiPaginatesWithTokenAndNeverLogsKey() {
        stub.on("/search.json",
                Reply.json("""
                        {"jobs_results":[{"title":"Java Developer","company_name":"TEST DATA S","location":"Anywhere","via":"LinkedIn",
                          "description":"Remote role","detected_extensions":{"posted_at":"3 days ago","schedule_type":"Full-time","work_from_home":true},
                          "apply_options":[{"title":"LinkedIn","link":"https://example.test/s/1"}],"job_id":"abc"}],
                         "serpapi_pagination":{"next_page_token":"TOKEN2"}}"""),
                Reply.json("""
                        {"jobs_results":[{"title":"Java Engineer","company_name":"TEST DATA T","location":"Bolivia",
                          "description":"x","detected_extensions":{},"apply_options":[{"link":"https://example.test/s/2"}],"job_id":"def"}]}"""));
        FetchResult r = new SerpApiConnector(http, props).fetch(new SourceQuery("java", List.of("BO"), true, null, 20, null, null),
                Map.of("apiKey", "SECRET"));
        assertThat(r.items()).hasSize(2);
        assertThat(r.requests()).isEqualTo(2);
        assertThat(stub.requests.get(1)).contains("next_page_token=TOKEN2").contains("gl=bo");
        assertThat(r.items().get(0).publishedApprox()).isTrue();
        assertThat(r.items().get(0).attribution()).contains("LinkedIn");
        assertThat(r.items().get(0).remote()).isTrue();
    }

    @Test
    void adzunaSkipsUnsupportedCountriesAndFlagsPredictedSalary() {
        stub.on("/v1/api/jobs/gb/search/1", Reply.json("""
                {"count":1,"results":[{"id":"42","title":"PHP Developer","description":"Laravel snippet","created":"2026-11-20T10:00:00Z",
                  "redirect_url":"https://example.test/adz/42","salary_min":40000,"salary_max":50000,"salary_is_predicted":"1",
                  "contract_time":"full_time","location":{"display_name":"London, UK"},"company":{"display_name":"TEST DATA Z"}}]}"""));
        FetchResult r = new AdzunaConnector(http, props).fetch(new SourceQuery("php", List.of("bo", "gb"), false, 7, 20, null, null),
                Map.of("appId", "id", "appKey", "key"));
        assertThat(r.items()).hasSize(1);
        assertThat(r.items().get(0).salaryEstimated()).isTrue();
        assertThat(r.items().get(0).salaryCurrency()).isEqualTo("GBP");
        assertThat(r.items().get(0).salaryPeriod()).isNull();
        assertThat(stub.requests).noneMatch(s -> s.contains("/jobs/bo/"));
    }

    @Test
    void retriesOn429ThenSucceeds() {
        stub.on("/api/remote-jobs", new Reply(429, "{}", Map.of("Retry-After", "0")), Reply.json("{\"jobs\":[]}"));
        FetchResult r = new RemotiveConnector(http, props).fetch(query(null), Map.of());
        assertThat(r.items()).isEmpty();
        assertThat(stub.requests).hasSize(2);
    }

    @Test
    void ssrfProtectionRejectsPrivateAddressesAndUnlistedHosts() {
        SafeHttpClient.Policy strict = SafeHttpClient.Policy.of("strict", Set.of("127.0.0.1"), Duration.ofSeconds(2), 0, 0);
        assertThatThrownBy(() -> http.get(strict, java.net.URI.create(stub.baseUrl() + "/x"), null))
                .hasMessageContaining("HTTPS");
        SafeHttpClient.Policy https = SafeHttpClient.Policy.of("https", Set.of("localhost"), Duration.ofSeconds(2), 0, 0);
        assertThatThrownBy(() -> http.get(https, java.net.URI.create("https://localhost/x"), null))
                .hasMessageContaining("non-public");
        assertThatThrownBy(() -> http.get(https, java.net.URI.create("https://evil.example/x"), null))
                .hasMessageContaining("not allowed");
    }
}
