package com.jobhunter.optimizer;

import com.jobhunter.ai.domain.AiOutputs;
import com.jobhunter.optimizer.domain.FactChecker;
import com.jobhunter.optimizer.domain.OptimizedResumeAssembler;
import com.jobhunter.profiles.application.ProfileSnapshotService.Contact;
import com.jobhunter.profiles.application.ProfileSnapshotService.ExperienceFact;
import com.jobhunter.profiles.application.ProfileSnapshotService.Fact;
import com.jobhunter.profiles.application.ProfileSnapshotService.LanguageFact;
import com.jobhunter.profiles.application.ProfileSnapshotService.PreferencesFact;
import com.jobhunter.profiles.application.ProfileSnapshotService.ProjectFact;
import com.jobhunter.profiles.application.ProfileSnapshotService.SkillFact;
import com.jobhunter.profiles.application.ProfileSnapshotService.Snapshot;
import com.jobhunter.resumes.domain.ResumeDocument;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** TEST DATA — a fictitious verified profile. */
class FactCheckerTest {

    static final Snapshot SNAPSHOT = new Snapshot(1L, "Java Backend", "Backend Developer", "Desarrolladora backend.", null,
            List.of(new ExperienceFact("exp:1", "TEST DATA Acme", "Backend Developer", "FULL_TIME", "REMOTE", null, "BO",
                    LocalDate.of(2022, 1, 1), null, true, "APIs REST para pagos",
                    List.of(new Fact("ach:1:0", "Reduje el tiempo de respuesta en un 30%")), List.of("skill:1", "skill:2"))),
            List.of(new ProjectFact("prj:7", "PERSONAL", "TEST DATA Side App", null, "App en Laravel", null, null, null,
                    List.of(), List.of("skill:3"))),
            List.of(), List.of(),
            List.of(new SkillFact("skill:1", "Java", "PROGRAMMING_LANGUAGE", "PROFESSIONAL", new BigDecimal("4.9")),
                    new SkillFact("skill:2", "Spring Boot", "FRAMEWORK", "PROFESSIONAL", new BigDecimal("4.9")),
                    new SkillFact("skill:3", "Laravel", "FRAMEWORK", "PROJECT_ONLY", BigDecimal.ZERO)),
            List.of(new LanguageFact("lang:en", "en", "B2")),
            new PreferencesFact(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null, List.of(), null, false),
            new BigDecimal("4.9"), 0);

    static final Map<String, Long> TECH = Map.of("Java", 1L, "Spring Boot", 2L, "Laravel", 3L, "Kubernetes", 99L);

    static List<Long> finder(String text) {
        List<Long> ids = new ArrayList<>();
        TECH.forEach((name, id) -> {
            if (Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(text).find()) ids.add(id);
        });
        return ids;
    }

    private final FactChecker checker = new FactChecker(SNAPSHOT, FactCheckerTest::finder);

    @Test
    void groundedBulletPasses() {
        var r = checker.check("experience", "exp:1", "Reduced response time by 30% building Java APIs", List.of("ach:1:0", "skill:1"), true);
        assertThat(r.grounded()).isTrue();
        assertThat(r.validRefs()).contains("ach:1:0", "skill:1", "exp:1");
    }

    @Test
    void inventedMetricIsSuggested() {
        var r = checker.check("experience", "exp:1", "Cut costs by 45%", List.of("ach:1:0"), true);
        assertThat(r.grounded()).isFalse();
        assertThat(r.violations()).extracting(FactChecker.Violation::rule).contains("UNSUPPORTED_NUMBER");
        assertThat(r.note()).contains("45%");
    }

    @Test
    void numbersAreComparedAsWholeTokens() {
        // "2" must not be considered supported just because the job started in 2022
        var r = checker.check("experience", "exp:1", "Managed a budget of 2 million", List.of("exp:1"), true);
        assertThat(r.violations()).extracting(FactChecker.Violation::rule).contains("UNSUPPORTED_NUMBER");
    }

    @Test
    void unknownTechnologyIsSuggested() {
        var r = checker.check("experience", "exp:1", "Deployed services on Kubernetes", List.of("exp:1"), true);
        assertThat(r.violations()).extracting(FactChecker.Violation::rule).contains("UNVERIFIED_TECHNOLOGY");
    }

    @Test
    void projectOnlySkillCannotBeClaimedInsideEmployment() {
        var r = checker.check("experience", "exp:1", "Built a Laravel admin panel", List.of("exp:1"), true);
        assertThat(r.grounded()).isFalse();
        var p = checker.check("projects", "prj:7", "Built a Laravel admin panel", List.of("prj:7"), false);
        assertThat(p.grounded()).isTrue();
    }

    @Test
    void projectRefInEmploymentIsRejected() {
        var r = checker.check("experience", "exp:1", "Built an app", List.of("prj:7"), true);
        assertThat(r.violations()).extracting(FactChecker.Violation::rule).contains("PROJECT_AS_EMPLOYMENT");
    }

    @Test
    void inflatedYearsAreFlagged() {
        var r = checker.check("summary", null, "Backend developer with 8 years of Java experience", List.of("skill:1"), false);
        assertThat(r.violations()).extracting(FactChecker.Violation::rule).contains("YEARS_EXCEEDED");
        var ok = checker.check("summary", null, "Backend developer with 4 years of Java experience", List.of("skill:1"), false);
        assertThat(ok.grounded()).isTrue();
    }

    @Test
    void assemblerKeepsVerifiedSkeletonAndDropsInventedEntries() {
        var ai = new AiOutputs.CvOptimize("Java Backend Engineer",
                new AiOutputs.RefText("Backend developer focused on Java and Spring Boot.", List.of("skill:1", "skill:2")),
                List.of(new AiOutputs.EntryBullets("exp:1", List.of(
                                new AiOutputs.RefText("Built payment REST APIs with Spring Boot", List.of("exp:1", "skill:2")),
                                new AiOutputs.RefText("Led a team of 12 engineers", List.of("exp:1")))),
                        new AiOutputs.EntryBullets("exp:999", List.of()),
                        new AiOutputs.EntryBullets("prj:7", List.of())),
                List.of(), List.of("skill:2", "skill:1"), false);
        var assembled = OptimizedResumeAssembler.assemble("Ana Prueba", SNAPSHOT,
                new Contact("ana@example.com", null, "Cochabamba", "BO", null, null, null, null), "en", ai, checker,
                "DEEPSEEK", "deepseek-v4-pro", 5L);
        ResumeDocument doc = assembled.document();
        assertThat(doc.experience()).hasSize(1);
        assertThat(doc.experience().get(0).company()).isEqualTo("TEST DATA Acme");
        assertThat(doc.experience().get(0).bullets()).extracting(ResumeDocument.Bullet::status)
                .containsExactly(ResumeDocument.BulletStatus.GROUNDED, ResumeDocument.BulletStatus.SUGGESTED);
        assertThat(doc.experience().get(0).bullets().get(1).note()).contains("12");
        assertThat(doc.summary()).isEqualTo("Backend developer focused on Java and Spring Boot.");
        assertThat(doc.basics().headline()).isEqualTo("Java Backend Engineer");
        assertThat(doc.skills().get(0).items().get(0).name()).isEqualTo("Spring Boot");
        assertThat(doc.projects()).extracting(ResumeDocument.ProjectEntry::name).containsExactly("TEST DATA Side App");
        assertThat(assembled.report().violations()).extracting(FactChecker.Violation::rule)
                .contains("DROPPED_ENTRY", "PROJECT_AS_EMPLOYMENT", "UNSUPPORTED_NUMBER");
        assertThat(assembled.report().suggestedCount()).isEqualTo(1);
    }
}
