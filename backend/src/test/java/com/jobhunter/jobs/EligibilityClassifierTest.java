package com.jobhunter.jobs;

import com.jobhunter.jobs.domain.EligibilityClassifier;
import com.jobhunter.jobs.domain.JobClassification;
import com.jobhunter.jobs.domain.JobOfferDraft;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** TEST DATA — synthetic postings. */
class EligibilityClassifierTest {

    private static JobOfferDraft.Builder job(String source) {
        return JobOfferDraft.builder(source, "x").title("Backend Developer").company("TEST DATA Corp");
    }

    @Test
    void himalayasListIncludingBoliviaIsEligible() {
        JobClassification c = EligibilityClassifier.classify(job("himalayas")
                .allowedLocations(List.of("Argentina", "Bolivia", "Brazil", "United States")).build());
        assertThat(c.workModality()).isEqualTo("REMOTE");
        assertThat(c.boliviaEligibility()).isEqualTo("ELIGIBLE");
        assertThat(c.remoteCountries()).contains("BO", "AR");
        assertThat(c.evidence()).contains("Bolivia");
    }

    @Test
    void structuredListWithoutBoliviaIsNotEligible() {
        JobClassification c = EligibilityClassifier.classify(job("himalayas")
                .allowedLocations(List.of("Germany", "Spain", "Portugal")).build());
        assertThat(c.boliviaEligibility()).isEqualTo("NOT_ELIGIBLE");
        assertThat(c.remoteScope()).isEqualTo("EUROPE");
    }

    @Test
    void remotiveLatamTextIsLikely() {
        JobClassification c = EligibilityClassifier.classify(job("remotive")
                .location("Americas, LATAM, Canada, USA timezones").build());
        assertThat(c.remoteScope()).isEqualTo("LATAM");
        assertThat(c.boliviaEligibility()).isEqualTo("LIKELY");
        assertThat(c.evidence()).contains("LATAM");
    }

    @Test
    void usOnlyIsNotEligibleEvenIfRemote() {
        JobClassification c = EligibilityClassifier.classify(job("serpapi_google_jobs").remote(true)
                .location("Remote").descriptionText("This is a remote role. Candidates must be based in the US.").build());
        assertThat(c.boliviaEligibility()).isEqualTo("NOT_ELIGIBLE");
        assertThat(c.evidence()).contains("must be based in the US");
    }

    @Test
    void worldwideInLocationIsEligible() {
        JobClassification c = EligibilityClassifier.classify(job("remotive").location("Worldwide").build());
        assertThat(c.remoteScope()).isEqualTo("WORLDWIDE");
        assertThat(c.boliviaEligibility()).isEqualTo("ELIGIBLE");
    }

    @Test
    void plainRemoteWithoutEvidenceStaysUnknown() {
        JobClassification c = EligibilityClassifier.classify(job("greenhouse").workplaceType("remote")
                .location("Remote").descriptionText("We build software.").build());
        assertThat(c.workModality()).isEqualTo("REMOTE");
        assertThat(c.boliviaEligibility()).isEqualTo("UNKNOWN");
    }

    @Test
    void timezoneOutsideBoliviaIsNotEligible() {
        JobClassification c = EligibilityClassifier.classify(job("himalayas").timezoneOffsets(List.of(0, 1, 2)).build());
        assertThat(c.boliviaEligibility()).isEqualTo("NOT_ELIGIBLE");
        assertThat(c.remoteScope()).isEqualTo("TIMEZONE_RESTRICTED");
    }

    @Test
    void timezoneRangeInTextIncludingBoliviaIsLikely() {
        JobClassification c = EligibilityClassifier.classify(job("greenhouse").workplaceType("remote")
                .descriptionText("You must overlap with UTC-5 to UTC-3 working hours.").build());
        assertThat(c.boliviaEligibility()).isEqualTo("LIKELY");
    }

    @Test
    void onsiteAbroadWithoutSponsorshipIsNotEligible() {
        JobClassification c = EligibilityClassifier.classify(job("lever").workplaceType("onsite")
                .location("Berlin, Germany").descriptionText("Office-based role.").build());
        assertThat(c.country()).isEqualTo("DE");
        assertThat(c.boliviaEligibility()).isEqualTo("NOT_ELIGIBLE");
        assertThat(c.engagementType()).isEqualTo("EMPLOYEE_LOCAL");
    }

    @Test
    void onsiteAbroadWithSponsorshipIsLikelyAndSponsored() {
        JobClassification c = EligibilityClassifier.classify(job("lever").workplaceType("onsite")
                .location("Amsterdam, Netherlands").descriptionText("We offer visa sponsorship and relocation support.").build());
        assertThat(c.boliviaEligibility()).isEqualTo("LIKELY");
        assertThat(c.engagementType()).isEqualTo("SPONSORED_RELOCATION");
    }

    @Test
    void contractorAndLanguagesAreDetected() {
        JobClassification c = EligibilityClassifier.classify(job("remotive").location("Worldwide").employmentType("contract")
                .descriptionText("Independent contractor role. Fluent English required; German B2 is a plus.").build());
        assertThat(c.engagementType()).isEqualTo("CONTRACTOR");
        assertThat(c.contractType()).isEqualTo("CONTRACT");
        assertThat(c.requiredLanguages()).extracting(JobClassification.RequiredLanguage::code).contains("en", "de");
    }

    @Test
    void seniorityFromTitle() {
        assertThat(EligibilityClassifier.classify(job("remotive").title("Sr. Java Engineer").build()).seniority()).isEqualTo("SENIOR");
        assertThat(EligibilityClassifier.classify(job("remotive").title("Junior PHP Developer").build()).seniority()).isEqualTo("JUNIOR");
    }
}
