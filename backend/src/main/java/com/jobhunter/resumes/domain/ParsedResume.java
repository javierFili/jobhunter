package com.jobhunter.resumes.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Best-effort structure detected in an uploaded CV. Everything here is UNVERIFIED until the user reviews it and
 * imports it into the profile. {@code rawText} keeps the original fragment so the user can correct the parse.
 */
public record ParsedResume(
        Contact contact,
        String summary,
        List<ExperienceItem> experiences,
        List<EducationItem> educations,
        List<ProjectItem> projects,
        List<CertificationItem> certifications,
        List<SkillItem> skills,
        List<LanguageItem> languages,
        Map<String, String> sections,
        List<String> warnings) {

    public record Contact(String fullName, String email, String phone, String githubUrl, String linkedinUrl,
                          String websiteUrl, String location) {
    }

    public record ExperienceItem(String positionTitle, String companyName, LocalDate startDate, LocalDate endDate,
                                 boolean current, String description, List<String> achievements, List<Long> skillIds,
                                 String rawText) {
    }

    public record EducationItem(String institution, String degree, LocalDate startDate, LocalDate endDate,
                                String rawText) {
    }

    public record ProjectItem(String name, String description, List<Long> skillIds, String rawText) {
    }

    public record CertificationItem(String name, String issuer, LocalDate issuedAt, String rawText) {
    }

    public record SkillItem(Long skillId, String name) {
    }

    public record LanguageItem(String languageCode, String level, String rawText) {
    }
}
