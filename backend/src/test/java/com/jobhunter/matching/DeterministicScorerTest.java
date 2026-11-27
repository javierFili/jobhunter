package com.jobhunter.matching;

import com.jobhunter.matching.domain.DeterministicScorer;
import com.jobhunter.matching.domain.DeterministicScorer.JobInput;
import com.jobhunter.matching.domain.DeterministicScorer.LanguageRequirement;
import com.jobhunter.matching.domain.DeterministicScorer.ProfileInput;
import com.jobhunter.matching.domain.DeterministicScorer.SkillFact;
import com.jobhunter.matching.domain.DeterministicScorer.SkillRequirement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicScorerTest {

    private static ProfileInput profile(Map<Long, SkillFact> skills, String years) {
        return new ProfileInput(skills, new BigDecimal(years), Map.of("es", "NATIVE", "en", "B2"), null,
                new BigDecimal("2000"), List.of(), List.of(), List.of());
    }

    private static JobInput job(String eligibility, List<SkillRequirement> skills, BigDecimal minYears, String seniority) {
        return new JobInput(eligibility, "evidence", "REMOTE", null, seniority, minYears, skills, true,
                List.of(new LanguageRequirement("en", "B2", true)), new BigDecimal("3000"), null, "FULL_TIME", "CONTRACTOR");
    }

    @Test
    void strongProfessionalMatchIsHigh() {
        var p = profile(Map.of(1L, new SkillFact(1, "Java", "PROFESSIONAL", new BigDecimal("4")),
                2L, new SkillFact(2, "Spring Boot", "PROFESSIONAL", new BigDecimal("3"))), "4");
        var s = DeterministicScorer.score(p, job("ELIGIBLE", List.of(
                new SkillRequirement(1L, "Java", true, new BigDecimal("3")),
                new SkillRequirement(2L, "Spring Boot", true, null)), new BigDecimal("3"), "MID"));
        assertThat(s.classification()).isEqualTo("HIGH_MATCH");
        assertThat(s.recommendation()).isEqualTo("APPLY");
        assertThat(s.missingMandatory()).isEmpty();
        assertThat(s.dimensions().get("compensation").score()).isEqualTo(100);
    }

    @Test
    void projectOnlySkillsScoreLowerThanProfessional() {
        var pro = profile(Map.of(1L, new SkillFact(1, "Laravel", "PROFESSIONAL", new BigDecimal("2"))), "2");
        var side = profile(Map.of(1L, new SkillFact(1, "Laravel", "PROJECT_ONLY", BigDecimal.ZERO)), "2");
        var j = job("ELIGIBLE", List.of(new SkillRequirement(1L, "Laravel", true, null)), null, null);
        assertThat(DeterministicScorer.score(side, j).dimensions().get("skills").score())
                .isLessThan(DeterministicScorer.score(pro, j).dimensions().get("skills").score());
        assertThat(DeterministicScorer.score(side, j).gaps()).anyMatch(g -> g.contains("solo en proyectos"));
    }

    @Test
    void missingMandatorySkillPreventsHighMatch() {
        var p = profile(Map.of(1L, new SkillFact(1, "Java", "PROFESSIONAL", new BigDecimal("6"))), "6");
        var s = DeterministicScorer.score(p, job("ELIGIBLE", List.of(new SkillRequirement(1L, "Java", true, null),
                new SkillRequirement(99L, "Kubernetes", true, null)), null, "SENIOR"));
        assertThat(s.missingMandatory()).containsExactly("Kubernetes");
        assertThat(s.classification()).isNotEqualTo("HIGH_MATCH");
    }

    @Test
    void notEligibleAndUnknownRemote() {
        var p = profile(Map.of(), "1");
        assertThat(DeterministicScorer.score(p, job("NOT_ELIGIBLE", List.of(), null, null)).classification()).isEqualTo("NOT_ELIGIBLE");
        assertThat(DeterministicScorer.score(p, job("UNKNOWN", List.of(), null, null)).classification()).isEqualTo("NEEDS_REVIEW");
    }

    @Test
    void languageBelowRequiredLevelIsAGap() {
        var p = new ProfileInput(Map.of(), BigDecimal.ONE, Map.of("en", "B1"), null, null, List.of(), List.of(), List.of());
        var s = DeterministicScorer.score(p, job("ELIGIBLE", List.of(), null, null));
        assertThat(s.dimensions().get("language").score()).isEqualTo(60);
        assertThat(s.gaps()).anyMatch(g -> g.contains("EN"));
    }

    @Test
    void weightsSumTo100() {
        assertThat(DeterministicScorer.WEIGHTS.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(100);
    }
}
