package com.jobhunter.jobsources;

import com.jobhunter.jobs.domain.EligibilityClassifier;
import com.jobhunter.jobs.domain.JobClassification;
import com.jobhunter.jobs.domain.JobOfferDraft;
import com.jobhunter.jobs.domain.JobText;
import com.jobhunter.jobsources.domain.FetchResult;
import com.jobhunter.jobsources.domain.JobSourceConnector;
import com.jobhunter.jobsources.domain.SourceQuery;
import com.jobhunter.jobsources.infrastructure.AshbyConnector;
import com.jobhunter.jobsources.infrastructure.ConnectorProperties;
import com.jobhunter.jobsources.infrastructure.GreenhouseConnector;
import com.jobhunter.jobsources.infrastructure.HimalayasConnector;
import com.jobhunter.jobsources.infrastructure.JobicyConnector;
import com.jobhunter.jobsources.infrastructure.LeverConnector;
import com.jobhunter.jobsources.infrastructure.RemoteOkConnector;
import com.jobhunter.jobsources.infrastructure.RemotiveConnector;
import com.jobhunter.shared.http.SafeHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MANUAL check against the REAL free, keyless public APIs (one request each, within their rate guidance).
 * Never runs in normal builds: ./mvnw test -Dtest=LiveConnectorsManualTest -Dlive=true
 */
@EnabledIfSystemProperty(named = "live", matches = "true")
class LiveConnectorsManualTest {

    private final SafeHttpClient http = new SafeHttpClient();
    private final ConnectorProperties props = new ConnectorProperties(Map.of(), false);

    @Test
    void realSources() {
        List<Object[]> runs = List.of(
                new Object[]{new HimalayasConnector(http, props), new SourceQuery("java", List.of(), true, null, 20, null, null)},
                new Object[]{new RemotiveConnector(http, props), new SourceQuery(null, List.of(), true, null, 50, null, null)},
                new Object[]{new JobicyConnector(http, props), new SourceQuery("java", List.of(), true, null, 20, null, null)},
                new Object[]{new RemoteOkConnector(http, props), new SourceQuery(null, List.of(), true, null, 50, null, null)},
                new Object[]{new GreenhouseConnector(http, props), SourceQuery.board("gitlab", "GitLab", 500)},
                new Object[]{new LeverConnector(http, props), SourceQuery.board("leverdemo", "Lever demo", 100)},
                new Object[]{new AshbyConnector(http, props), SourceQuery.board("ashby", "Ashby", 200)});
        String only = System.getProperty("only");
        for (Object[] run : runs) {
            JobSourceConnector c = (JobSourceConnector) run[0];
            if (only != null && !only.equals(c.sourceCode())) continue;
            int shown = 0;
            FetchResult r = c.fetch((SourceQuery) run[1], Map.of());
            Map<String, Integer> eligibility = new TreeMap<>();
            Map<String, Integer> modality = new TreeMap<>();
            int withSalary = 0;
            for (JobOfferDraft d : r.items()) {
                String text = d.descriptionText() != null ? d.descriptionText() : JobText.htmlToText(d.descriptionHtml());
                JobOfferDraft withText = JobOfferDraft.builder(d.sourceCode(), d.externalId()).title(d.title())
                        .company(d.companyName()).location(d.locationText()).allowedLocations(d.allowedLocations())
                        .timezoneOffsets(d.timezoneOffsets()).remote(d.remote()).workplaceType(d.workplaceType())
                        .employmentType(d.employmentType()).countryHint(d.countryHint()).descriptionText(text).build();
                JobClassification cl = EligibilityClassifier.classify(withText);
                eligibility.merge(cl.boliviaEligibility(), 1, Integer::sum);
                if (only != null && "NOT_ELIGIBLE".equals(cl.boliviaEligibility()) && shown++ < 12) {
                    System.out.println("  NOT_ELIGIBLE [" + d.locationText() + "] " + cl.remoteScope() + " :: " + cl.evidence());
                }
                modality.merge(cl.workModality(), 1, Integer::sum);
                if (d.salaryMin() != null || d.salaryText() != null) withSalary++;
                assertThat(d.title()).isNotBlank();
                assertThat(d.sourceUrl()).startsWith("http");
            }
            System.out.printf("%-12s items=%3d requests=%d salary=%3d eligibility=%s modality=%s%n", c.sourceCode(),
                    r.items().size(), r.requests(), withSalary, eligibility, modality);
        }
    }
}
