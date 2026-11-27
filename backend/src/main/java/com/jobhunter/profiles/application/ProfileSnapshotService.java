package com.jobhunter.profiles.application;

import com.jobhunter.profiles.domain.Certification;
import com.jobhunter.profiles.domain.Education;
import com.jobhunter.profiles.domain.Experience;
import com.jobhunter.profiles.domain.JobPreferences;
import com.jobhunter.profiles.domain.ProfessionalProfile;
import com.jobhunter.profiles.domain.Project;
import com.jobhunter.profiles.domain.Skill;
import com.jobhunter.profiles.domain.UserLanguage;
import com.jobhunter.shared.json.Json;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Immutable view of the user's VERIFIED facts, each with a stable reference id. This is the only source the resume
 * builder and the AI may use: anything not in the snapshot must not appear in generated documents.
 * Contact data is kept apart ({@link Contact}) so it is never sent to AI providers.
 */
@Service
public class ProfileSnapshotService {

    private final ProfessionalProfileService profiles;
    private final ProfileItemsService items;
    private final SkillService skills;
    private final ProfileService profileService;

    public ProfileSnapshotService(ProfessionalProfileService profiles, ProfileItemsService items, SkillService skills,
                                  ProfileService profileService) {
        this.profiles = profiles;
        this.items = items;
        this.skills = skills;
        this.profileService = profileService;
    }

    public record Fact(String ref, String text) {
    }

    public record ExperienceFact(String ref, String company, String title, String employmentType, String modality,
                                 String location, String country, LocalDate startDate, LocalDate endDate,
                                 boolean current, String description, List<Fact> achievements, List<String> skillRefs) {
    }

    public record ProjectFact(String ref, String kind, String name, String role, String description, String url,
                              LocalDate startDate, LocalDate endDate, List<Fact> highlights, List<String> skillRefs) {
    }

    public record EducationFact(String ref, String institution, String degree, String field, LocalDate startDate,
                                LocalDate endDate, String status) {
    }

    public record CertificationFact(String ref, String name, String issuer, LocalDate issuedAt, String url) {
    }

    public record SkillFact(String ref, String name, String category, String evidence, BigDecimal yearsProfessional) {
    }

    public record LanguageFact(String ref, String code, String level) {
    }

    public record PreferencesFact(List<String> regions, List<String> countriesInclude, List<String> countriesExclude,
                                  List<String> modalities, List<String> contractTypes, List<String> engagementTypes,
                                  BigDecimal minSalary, String salaryCurrency, String salaryPeriod,
                                  List<String> workLanguages, String seniority, boolean relocationOk) {
    }

    /** Personal contact data — NEVER sent to external AI providers; re-inserted locally when rendering. */
    public record Contact(String email, String phone, String city, String country, String githubUrl,
                          String linkedinUrl, String portfolioUrl, String websiteUrl) {
    }

    public record Snapshot(Long profileId, String profileName, String headline, String summary, String targetRole,
                           List<ExperienceFact> experiences, List<ProjectFact> projects,
                           List<EducationFact> educations, List<CertificationFact> certifications,
                           List<SkillFact> skills, List<LanguageFact> languages, PreferencesFact preferences,
                           BigDecimal totalProfessionalYears, int unverifiedItemsSkipped) {

        /** Stable hash of the facts (not of contact data), used as part of AI cache keys. */
        public String hash() {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256")
                        .digest(Json.write(this).getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(digest);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        /** Every reference id present in the snapshot (for the factuality validator). */
        public java.util.Set<String> allRefs() {
            java.util.Set<String> refs = new java.util.HashSet<>();
            experiences.forEach(e -> {
                refs.add(e.ref());
                e.achievements().forEach(a -> refs.add(a.ref()));
            });
            projects.forEach(p -> {
                refs.add(p.ref());
                p.highlights().forEach(h -> refs.add(h.ref()));
            });
            educations.forEach(e -> refs.add(e.ref()));
            certifications.forEach(c -> refs.add(c.ref()));
            skills.forEach(s -> refs.add(s.ref()));
            languages.forEach(l -> refs.add(l.ref()));
            if (summary != null) refs.add("profile:summary");
            if (headline != null) refs.add("profile:headline");
            return refs;
        }
    }

    public record SnapshotWithContact(Snapshot snapshot, Contact contact) {
    }

    /**
     * @param profileId professional profile to use; null = the user's default profile (or all verified items if the
     *                  user has no profile yet)
     */
    @Transactional(readOnly = true)
    public SnapshotWithContact build(long userId, Long profileId) {
        ProfessionalProfile profile = profileId != null ? profiles.get(userId, profileId)
                : profiles.active(userId).orElse(null);

        List<Experience> allExperiences = items.listExperiences(userId);
        List<Project> allProjects = items.listProjects(userId);
        List<Education> allEducations = items.listEducations(userId);
        List<Certification> allCertifications = items.listCertifications(userId);
        List<SkillService.UserSkillView> allSkills = skills.list(userId);
        List<UserLanguage> allLanguages = profileService.listLanguages(userId);

        int skipped = (int) (allExperiences.stream().filter(e -> !e.isVerified()).count()
                + allProjects.stream().filter(p -> !p.isVerified()).count()
                + allEducations.stream().filter(e -> !e.isVerified()).count()
                + allCertifications.stream().filter(c -> !c.isVerified()).count()
                + allSkills.stream().filter(s -> !s.verified()).count()
                + allLanguages.stream().filter(l -> !l.isVerified()).count());

        List<Experience> exps = select(allExperiences, profile == null ? List.of() : profile.getExperienceIds(),
                Experience::getId).stream().filter(Experience::isVerified).toList();
        List<Project> prjs = select(allProjects, profile == null ? List.of() : profile.getProjectIds(),
                Project::getId).stream().filter(Project::isVerified).toList();
        List<SkillService.UserSkillView> sks = select(allSkills, profile == null ? List.of() : profile.getSkillIds(),
                SkillService.UserSkillView::skillId).stream().filter(SkillService.UserSkillView::verified).toList();

        Map<Long, Skill> catalogue = skills.byIds(allSkills.stream().map(SkillService.UserSkillView::skillId).toList());

        List<ExperienceFact> experienceFacts = exps.stream().map(e -> new ExperienceFact("exp:" + e.getId(),
                e.getCompanyName(), e.getPositionTitle(), e.getEmploymentType().name(),
                e.getWorkModality() == null ? null : e.getWorkModality().name(), e.getLocation(), e.getCountry(),
                e.getStartDate(), e.getEndDate(), e.isCurrent(), e.getDescription(),
                facts("ach:" + e.getId(), e.getAchievements()), skillRefs(e.getSkillIds(), catalogue))).toList();
        List<ProjectFact> projectFacts = prjs.stream().map(p -> new ProjectFact("prj:" + p.getId(), p.getKind().name(),
                p.getName(), p.getRole(), p.getDescription(), p.getUrl() != null ? p.getUrl() : p.getRepoUrl(),
                p.getStartDate(), p.getEndDate(), facts("hl:" + p.getId(), p.getHighlights()),
                skillRefs(p.getSkillIds(), catalogue))).toList();
        List<EducationFact> educationFacts = allEducations.stream().filter(Education::isVerified)
                .map(e -> new EducationFact("edu:" + e.getId(), e.getInstitution(), e.getDegree(), e.getFieldOfStudy(),
                        e.getStartDate(), e.getEndDate(), e.getStatus().name())).toList();
        List<CertificationFact> certificationFacts = allCertifications.stream().filter(Certification::isVerified)
                .map(c -> new CertificationFact("cert:" + c.getId(), c.getName(), c.getIssuer(), c.getIssuedAt(),
                        c.getCredentialUrl())).toList();
        List<SkillFact> skillFacts = sks.stream().map(s -> new SkillFact("skill:" + s.skillId(), s.name(), s.category(),
                s.evidenceLevel(), s.yearsProfessional())).toList();
        List<LanguageFact> languageFacts = allLanguages.stream().filter(UserLanguage::isVerified)
                .map(l -> new LanguageFact("lang:" + l.getLanguageCode(), l.getLanguageCode(), l.getLevel().name()))
                .toList();

        JobPreferences prefs = profileService.getPreferences(userId);
        PreferencesFact preferencesFact = new PreferencesFact(prefs.getRegions(), prefs.getCountriesInclude(),
                prefs.getCountriesExclude(), prefs.getModalities(), prefs.getContractTypes(),
                prefs.getEngagementTypes(), prefs.getMinSalary(), prefs.getSalaryCurrency(),
                prefs.getSalaryPeriod() == null ? null : prefs.getSalaryPeriod().name(), prefs.getWorkLanguages(),
                prefs.getSeniority() == null ? null : prefs.getSeniority().name(), prefs.isRelocationOk());

        BigDecimal totalYears = com.jobhunter.profiles.domain.ExperienceYears.of(allExperiences.stream()
                .filter(Experience::isVerified)
                .map(e -> new com.jobhunter.profiles.domain.ExperienceYears.Period(e.getStartDate(), e.getEndDate()))
                .toList(), LocalDate.now());

        Snapshot snapshot = new Snapshot(profile == null ? null : profile.getId(),
                profile == null ? null : profile.getName(), profile == null ? null : profile.getHeadline(),
                profile == null ? null : profile.getSummary(), profile == null ? null : profile.getTargetRole(),
                experienceFacts, projectFacts, educationFacts, certificationFacts, skillFacts, languageFacts,
                preferencesFact, totalYears, skipped);
        Contact contact = profile == null ? new Contact(null, null, null, null, null, null, null, null)
                : new Contact(profile.getContactEmail(), profile.getPhone(), profile.getCity(), profile.getCountry(),
                profile.getGithubUrl(), profile.getLinkedinUrl(), profile.getPortfolioUrl(), profile.getWebsiteUrl());
        return new SnapshotWithContact(snapshot, contact);
    }

    /** Selected ids (in the given order) when a selection exists; otherwise all items. */
    private static <T> List<T> select(List<T> all, List<Long> selectedIds, Function<T, Long> id) {
        if (selectedIds == null || selectedIds.isEmpty()) return all;
        Map<Long, T> byId = all.stream().collect(Collectors.toMap(id, t -> t, (a, b) -> a, LinkedHashMap::new));
        return selectedIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private static List<Fact> facts(String prefix, List<String> texts) {
        List<Fact> result = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            result.add(new Fact(prefix + ":" + i, texts.get(i)));
        }
        return result;
    }

    private static List<String> skillRefs(Collection<Long> ids, Map<Long, Skill> catalogue) {
        return ids.stream().filter(catalogue::containsKey).map(id -> "skill:" + id).toList();
    }
}
