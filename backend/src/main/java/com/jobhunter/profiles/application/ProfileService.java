package com.jobhunter.profiles.application;

import com.jobhunter.profiles.application.Commands.LanguageCommand;
import com.jobhunter.profiles.application.Commands.PreferencesCommand;
import com.jobhunter.profiles.application.Commands.VerifyCommand;
import com.jobhunter.profiles.domain.JobPreferences;
import com.jobhunter.profiles.domain.UserLanguage;
import com.jobhunter.profiles.infrastructure.CertificationRepository;
import com.jobhunter.profiles.infrastructure.EducationRepository;
import com.jobhunter.profiles.infrastructure.ExperienceRepository;
import com.jobhunter.profiles.infrastructure.JobPreferencesRepository;
import com.jobhunter.profiles.infrastructure.ProfessionalProfileRepository;
import com.jobhunter.profiles.infrastructure.ProjectRepository;
import com.jobhunter.profiles.infrastructure.UserLanguageRepository;
import com.jobhunter.profiles.infrastructure.UserSkillRepository;
import com.jobhunter.shared.api.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Languages, job preferences, bulk verification and profile completeness. */
@Service
public class ProfileService {

    private final UserLanguageRepository languages;
    private final JobPreferencesRepository preferences;
    private final ExperienceRepository experiences;
    private final ProjectRepository projects;
    private final EducationRepository educations;
    private final CertificationRepository certifications;
    private final UserSkillRepository userSkills;
    private final ProfessionalProfileRepository profiles;

    public ProfileService(UserLanguageRepository languages, JobPreferencesRepository preferences,
                          ExperienceRepository experiences, ProjectRepository projects, EducationRepository educations,
                          CertificationRepository certifications, UserSkillRepository userSkills,
                          ProfessionalProfileRepository profiles) {
        this.languages = languages;
        this.preferences = preferences;
        this.experiences = experiences;
        this.projects = projects;
        this.educations = educations;
        this.certifications = certifications;
        this.userSkills = userSkills;
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public List<UserLanguage> listLanguages(long userId) {
        return languages.findByUserIdOrderByLanguageCode(userId);
    }

    /** Replaces the whole set. Keeps the verified flag for languages whose level did not change. */
    @Transactional
    public List<UserLanguage> replaceLanguages(long userId, List<LanguageCommand> items) {
        Map<String, LanguageCommand> wanted = new LinkedHashMap<>();
        for (LanguageCommand c : items) {
            if (wanted.put(c.languageCode().toLowerCase(), c) != null) {
                throw ApiException.badRequest("VALIDATION_FAILED", "Idioma repetido: " + c.languageCode());
            }
        }
        List<UserLanguage> current = languages.findByUserIdOrderByLanguageCode(userId);
        Set<String> kept = new HashSet<>();
        for (UserLanguage existing : current) {
            LanguageCommand c = wanted.get(existing.getLanguageCode());
            if (c == null) {
                languages.delete(existing);
            } else {
                if (existing.getLevel() != c.level()) {
                    languages.delete(existing);
                    languages.flush();
                    languages.save(new UserLanguage(userId, c.languageCode(), c.level(), c.certificate(), true));
                } else {
                    existing.update(c.level(), c.certificate());
                }
                kept.add(existing.getLanguageCode());
            }
        }
        wanted.forEach((code, c) -> {
            if (!kept.contains(code)) {
                languages.save(new UserLanguage(userId, code, c.level(), c.certificate(), true));
            }
        });
        return languages.findByUserIdOrderByLanguageCode(userId);
    }

    /** Adds languages from a CV import as UNVERIFIED; never touches languages the user already has. */
    @Transactional
    public int importLanguages(long userId, List<LanguageCommand> items) {
        Set<String> existing = new HashSet<>();
        languages.findByUserIdOrderByLanguageCode(userId).forEach(l -> existing.add(l.getLanguageCode()));
        int added = 0;
        for (LanguageCommand c : items) {
            if (existing.add(c.languageCode().toLowerCase())) {
                languages.save(new UserLanguage(userId, c.languageCode(), c.level(), c.certificate(), false));
                added++;
            }
        }
        return added;
    }

    @Transactional
    public JobPreferences getPreferences(long userId) {
        return preferences.findByUserId(userId).orElseGet(() -> new JobPreferences(userId));
    }

    @Transactional
    public JobPreferences updatePreferences(long userId, PreferencesCommand c) {
        JobPreferences p = preferences.findByUserId(userId).orElseGet(() -> new JobPreferences(userId));
        p.update(names(c.regions()), c.countriesInclude(), c.countriesExclude(), names(c.modalities()),
                names(c.contractTypes()), names(c.engagementTypes()), c.minSalary(), c.salaryCurrency(),
                c.salaryPeriod(), c.workLanguages(), c.seniority(), c.relocationOk());
        return preferences.save(p);
    }

    private static List<String> names(List<? extends Enum<?>> values) {
        return values == null ? List.of() : values.stream().map(Enum::name).toList();
    }

    @Transactional
    public void verify(long userId, VerifyCommand c) {
        if (c.experienceIds() != null)
            experiences.findByUserIdAndIdIn(userId, c.experienceIds()).forEach(e -> e.markVerified());
        if (c.projectIds() != null)
            projects.findByUserIdAndIdIn(userId, c.projectIds()).forEach(p -> p.markVerified());
        if (c.educationIds() != null)
            educations.findByUserIdAndIdIn(userId, c.educationIds()).forEach(e -> e.markVerified());
        if (c.certificationIds() != null)
            certifications.findByUserIdAndIdIn(userId, c.certificationIds()).forEach(x -> x.markVerified());
        if (c.skillIds() != null)
            userSkills.findByUserIdAndSkillIdIn(userId, c.skillIds()).forEach(s -> s.markVerified());
        if (c.languageCodes() != null)
            languages.findByUserIdOrderByLanguageCode(userId).stream()
                    .filter(l -> c.languageCodes().contains(l.getLanguageCode())).forEach(UserLanguage::markVerified);
    }

    public record Completeness(int profileCompleteness, List<String> missingSections, Map<String, Long> counts) {
    }

    @Transactional(readOnly = true)
    public Completeness completeness(long userId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("profiles", profiles.countByUserId(userId));
        counts.put("experiences", experiences.countByUserId(userId));
        counts.put("projects", projects.countByUserId(userId));
        counts.put("educations", educations.countByUserId(userId));
        counts.put("certifications", certifications.countByUserId(userId));
        counts.put("skills", userSkills.countByUserId(userId));
        counts.put("languages", languages.countByUserId(userId));

        var defaultProfile = profiles.findByUserId(userId).stream()
                .filter(p -> p.isDefault()).findFirst().orElse(null);
        List<String> missing = new ArrayList<>();
        if (defaultProfile == null || defaultProfile.getHeadline() == null) missing.add("headline");
        if (defaultProfile == null || defaultProfile.getSummary() == null) missing.add("summary");
        if (counts.get("experiences") == 0 && counts.get("projects") == 0) missing.add("experience");
        if (counts.get("skills") < 3) missing.add("skills");
        if (counts.get("languages") == 0) missing.add("languages");
        if (preferences.findByUserId(userId).map(p -> p.getRegions().isEmpty() && p.getModalities().isEmpty()).orElse(true))
            missing.add("preferences");
        if (counts.get("educations") == 0) missing.add("education");
        int total = 7;
        int score = (int) Math.round(100.0 * (total - missing.size()) / total);
        return new Completeness(score, missing, counts);
    }
}
