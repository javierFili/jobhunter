package com.jobhunter.resumes.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Structured resume content (stored as JSON in resume_versions.content and rendered by HTML templates).
 * Every bullet keeps {@code sourceRefs} pointing at verified profile facts ("exp:12", "ach:12:0", "skill:5"...)
 * or is marked {@code SUGGESTED}, which requires explicit user confirmation before approval.
 */
public record ResumeDocument(
        @NotNull @Valid Basics basics,
        @Size(max = 3000) String summary,
        @NotNull @Size(max = 30) List<@Valid ExperienceEntry> experience,
        @NotNull @Size(max = 30) List<@Valid ProjectEntry> projects,
        @NotNull @Size(max = 20) List<@Valid EducationEntry> education,
        @NotNull @Size(max = 40) List<@Valid CertificationEntry> certifications,
        @NotNull @Size(max = 12) List<@Valid SkillGroup> skills,
        @NotNull @Size(max = 12) List<@Valid LanguageEntry> languages) {

    public enum BulletStatus { GROUNDED, SUGGESTED, USER_EDITED }

    public record Link(@Size(max = 40) String label, @Size(max = 500) @Pattern(regexp = "^https?://\\S+$") String url) {
    }

    public record Basics(@Size(max = 150) String fullName, @Size(max = 200) String headline,
                         @Size(max = 254) String email, @Size(max = 40) String phone,
                         @Size(max = 150) String location, @Size(max = 6) List<@Valid Link> links) {
    }

    /** {@code note} explains why a bullet is SUGGESTED (e.g. an unverified technology). */
    public record Bullet(@NotBlank @Size(max = 600) String text, @Size(max = 20) List<String> sourceRefs,
                         BulletStatus status, @Size(max = 300) String note) {

        public Bullet(String text, List<String> sourceRefs, BulletStatus status) {
            this(text, sourceRefs, status, null);
        }
    }

    public record ExperienceEntry(@Size(max = 40) String ref, @NotBlank @Size(max = 200) String company,
                                  @NotBlank @Size(max = 200) String title, @Size(max = 200) String location,
                                  @Size(max = 10) String startDate, @Size(max = 10) String endDate, boolean current,
                                  @Size(max = 15) List<@Valid Bullet> bullets) {
    }

    public record ProjectEntry(@Size(max = 40) String ref, @Size(max = 20) String kind,
                               @NotBlank @Size(max = 200) String name, @Size(max = 150) String role,
                               @Size(max = 500) String url, @Size(max = 10) List<@Valid Bullet> bullets) {
    }

    public record EducationEntry(@Size(max = 40) String ref, @NotBlank @Size(max = 200) String institution,
                                 @Size(max = 200) String degree, @Size(max = 200) String field,
                                 @Size(max = 10) String startDate, @Size(max = 10) String endDate,
                                 @Size(max = 20) String status) {
    }

    public record CertificationEntry(@Size(max = 40) String ref, @NotBlank @Size(max = 200) String name,
                                     @Size(max = 200) String issuer, @Size(max = 10) String date,
                                     @Size(max = 500) String url) {
    }

    public record SkillEntry(@Size(max = 40) String ref, @NotBlank @Size(max = 100) String name,
                             @Size(max = 20) String evidence) {
    }

    public record SkillGroup(@NotBlank @Size(max = 60) String label, @Size(max = 60) List<@Valid SkillEntry> items) {
    }

    public record LanguageEntry(@Size(max = 40) String ref, @NotBlank @Size(max = 5) String code,
                                @Size(max = 8) String level) {
    }
}
