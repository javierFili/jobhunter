package com.jobhunter.jobs;

import com.jobhunter.jobs.domain.Geo;
import com.jobhunter.jobs.domain.JobText;
import com.jobhunter.jobs.domain.SalaryParser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class JobTextTest {

    @Test
    void canonicalUrlDropsTrackingAndFragment() {
        assertThat(JobText.canonicalUrl("https://WWW.Example.com/jobs/123/?utm_source=x&gh_src=y&id=5#apply"))
                .isEqualTo("https://example.com/jobs/123?id=5");
        assertThat(JobText.canonicalUrl("javascript:alert(1)")).isNull();
    }

    @Test
    void sanitizeRemovesScriptsAndDecodesGreenhouseEntities() {
        String html = JobText.sanitizeHtml("&lt;p&gt;Hello &lt;script&gt;x()&lt;/script&gt;&lt;a href=&quot;https://a.b&quot; onclick=&quot;x()&quot;&gt;link&lt;/a&gt;&lt;/p&gt;");
        assertThat(html).contains("<p>Hello").doesNotContain("script").doesNotContain("onclick").contains("noopener");
    }

    @Test
    void htmlToTextKeepsParagraphs() {
        assertThat(JobText.htmlToText("<p>One</p><ul><li>Two</li><li>Three</li></ul>")).isEqualTo("One\nTwo\nThree");
    }

    @Test
    void normalizationForDedupe() {
        assertThat(JobText.normalizeCompany("Acme Software, Inc.")).isEqualTo("acme software");
        assertThat(JobText.normalizeCompany("Beispiel GmbH")).isEqualTo("beispiel");
        assertThat(JobText.normalizeTitle("Sr. Backend Dev (m/w/d)")).isEqualTo("senior backend developer");
    }

    @Test
    void simhashIsCloseForNearDuplicates() {
        String a = "We are looking for a backend developer with Java and Spring Boot experience to build APIs for our platform used by thousands";
        String b = "We are looking for a backend developer with Java and Spring Boot experience to build APIs for our platform used by millions";
        String c = "Marketing manager position in our Berlin office with focus on brand campaigns and events across Europe";
        assertThat(JobText.hamming(JobText.simhash(a), JobText.simhash(b))).isLessThan(JobText.hamming(JobText.simhash(a), JobText.simhash(c)));
    }

    @Test
    void salaries() {
        var s = SalaryParser.parse("$57K - $67K").orElseThrow();
        assertThat(s.min()).isEqualByComparingTo("57000");
        assertThat(s.max()).isEqualByComparingTo("67000");
        assertThat(s.currency()).isEqualTo("USD");
        assertThat(s.period()).isEqualTo("YEAR");
        var h = SalaryParser.parse("€40–€50 an hour").orElseThrow();
        assertThat(h.currency()).isEqualTo("EUR");
        assertThat(h.period()).isEqualTo("HOUR");
        var mth = SalaryParser.parse("USD 3,000 / mes").orElseThrow();
        assertThat(mth.min()).isEqualByComparingTo("3000");
        assertThat(mth.period()).isEqualTo("MONTH");
        assertThat(SalaryParser.monthly(new BigDecimal("60000"), "YEAR")).isEqualByComparingTo("5000");
        assertThat(SalaryParser.parse("Competitive")).isEmpty();
    }

    @Test
    void geo() {
        assertThat(Geo.iso("Bolivia")).contains("BO");
        assertThat(Geo.iso("Alemania")).contains("DE");
        assertThat(Geo.iso("USA")).contains("US");
        assertThat(Geo.findIn("Berlin, Germany")).contains("DE");
        assertThat(Geo.region("BO")).isEqualTo("LATAM");
        assertThat(Geo.region("ES")).isEqualTo("EUROPE");
    }
}
