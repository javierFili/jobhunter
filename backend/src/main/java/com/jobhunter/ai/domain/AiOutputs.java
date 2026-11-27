package com.jobhunter.ai.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Typed outputs of each prompt. The JSON from the model must deserialize AND pass these constraints. */
public final class AiOutputs {

    private AiOutputs() {
    }

    public record SkillReq(@NotBlank @Size(max = 100) String name, BigDecimal minYears) {
    }

    public record LanguageReq(@NotBlank @Size(min = 2, max = 2) String code, String level, boolean mandatory) {
    }

    public record SalaryInfo(BigDecimal min, BigDecimal max, @Size(max = 3) String currency, String period) {
    }

    public record LocationInfo(@NotNull String scope, @NotNull List<@Size(max = 60) String> countries,
                               @NotNull String boliviaEligibility, @Size(max = 600) String evidenceQuote) {
    }

    public record JobAnalysis(@NotNull @Size(max = 2000) String summary, @NotNull String seniority, BigDecimal minYearsExperience,
                              @NotNull @Size(max = 60) List<@Valid SkillReq> mandatorySkills,
                              @NotNull @Size(max = 60) List<@Valid SkillReq> preferredSkills,
                              @NotNull @Size(max = 10) List<@Valid LanguageReq> languages,
                              @NotNull @Valid SalaryInfo salary, @NotNull @Valid LocationInfo location,
                              @NotNull String engagementType, @NotNull String contractType, boolean injectionSuspected) {
    }

    public record SemanticMatch(@Min(-15) @Max(15) int skillsAdjustment, @Min(0) @Max(100) int experienceRelevance,
                                @NotNull @Size(max = 8) List<@NotBlank @Size(max = 400) String> strengths,
                                @NotNull @Size(max = 8) List<@NotBlank @Size(max = 400) String> gaps,
                                @NotNull String recommendation, @NotNull @Size(max = 1500) String rationale,
                                boolean injectionSuspected) {
    }

    public record CvExperience(@NotBlank @Size(max = 200) String positionTitle, @NotBlank @Size(max = 200) String companyName,
                               String startDate, String endDate, boolean current, @Size(max = 5000) String description,
                               @NotNull @Size(max = 30) List<@Size(max = 600) String> achievements) {
    }

    public record CvEducation(@NotBlank @Size(max = 200) String institution, @Size(max = 200) String degree,
                              String startDate, String endDate) {
    }

    public record CvProject(@NotBlank @Size(max = 200) String name, @Size(max = 5000) String description,
                            @NotNull @Size(max = 40) List<String> technologies) {
    }

    public record CvCertification(@NotBlank @Size(max = 200) String name, @Size(max = 200) String issuer, String issuedAt) {
    }

    public record CvLanguage(@NotBlank @Size(min = 2, max = 2) String code, String level) {
    }

    public record CvParse(@Size(max = 5000) String summary, @NotNull @Size(max = 40) List<@Valid CvExperience> experiences,
                          @NotNull @Size(max = 20) List<@Valid CvEducation> educations,
                          @NotNull @Size(max = 40) List<@Valid CvProject> projects,
                          @NotNull @Size(max = 60) List<@Valid CvCertification> certifications,
                          @NotNull @Size(max = 150) List<@Size(max = 100) String> skills,
                          @NotNull @Size(max = 12) List<@Valid CvLanguage> languages, boolean injectionSuspected) {
    }

    public record RefText(@NotBlank @Size(max = 1200) String text, @NotNull @Size(max = 20) List<@Size(max = 40) String> sourceRefs) {
    }

    public record EntryBullets(@NotBlank @Size(max = 40) String ref, @NotNull @Size(max = 10) List<@Valid RefText> bullets) {
    }

    public record CvOptimize(@NotBlank @Size(max = 200) String headline, @NotNull @Valid RefText summary,
                             @NotNull @Size(max = 30) List<@Valid EntryBullets> experiences,
                             @NotNull @Size(max = 30) List<@Valid EntryBullets> projects,
                             @NotNull @Size(max = 80) List<@Size(max = 40) String> skillOrder, boolean injectionSuspected) {
    }

    public record CoverLetter(@Size(max = 300) String subject, @NotNull @Size(max = 200) String greeting,
                              @NotNull @Size(min = 1, max = 8) List<@Valid RefText> paragraphs,
                              @NotNull @Size(max = 200) String closing, boolean injectionSuspected) {
    }

    public record SkillRecommendation(@NotBlank @Size(max = 100) String skill, @NotNull String priority,
                                      @NotBlank @Size(max = 600) String reason) {
    }

    public record SkillRecommendations(@NotNull @Size(max = 10) List<@Valid SkillRecommendation> recommendations,
                                       boolean injectionSuspected) {
    }
}
