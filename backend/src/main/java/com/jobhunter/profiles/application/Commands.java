package com.jobhunter.profiles.application;

import com.jobhunter.profiles.domain.Enums.ContractType;
import com.jobhunter.profiles.domain.Enums.EducationStatus;
import com.jobhunter.profiles.domain.Enums.EmploymentType;
import com.jobhunter.profiles.domain.Enums.EngagementType;
import com.jobhunter.profiles.domain.Enums.EvidenceLevel;
import com.jobhunter.profiles.domain.Enums.LanguageLevel;
import com.jobhunter.profiles.domain.Enums.ProjectKind;
import com.jobhunter.profiles.domain.Enums.Region;
import com.jobhunter.profiles.domain.Enums.SalaryPeriod;
import com.jobhunter.profiles.domain.Enums.Seniority;
import com.jobhunter.profiles.domain.Enums.SkillCategory;
import com.jobhunter.profiles.domain.Enums.WorkModality;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Validated input commands of the profile module (also used as request bodies). */
public final class Commands {

    public static final String URL = "^https?://\\S{1,490}$";
    public static final String COUNTRY = "^[A-Z]{2}$";
    public static final String LANG = "^[a-z]{2}$";

    private Commands() {
    }

    public record ProfileCommand(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 200) String headline,
            @Size(max = 5000) String summary,
            @Size(max = 150) String targetRole,
            Boolean isDefault,
            @Email @Size(max = 254) String contactEmail,
            @Size(max = 40) String phone,
            @Size(max = 100) String city,
            @Pattern(regexp = COUNTRY) String country,
            @Pattern(regexp = URL) String githubUrl,
            @Pattern(regexp = URL) String linkedinUrl,
            @Pattern(regexp = URL) String portfolioUrl,
            @Pattern(regexp = URL) String websiteUrl,
            Integer version) {
    }

    public record ProfileItemsCommand(@NotNull List<Long> experienceIds, @NotNull List<Long> projectIds,
                                      @NotNull List<Long> skillIds) {
    }

    public record ExperienceCommand(
            @NotBlank @Size(max = 200) String companyName,
            @Pattern(regexp = URL) String companyUrl,
            @NotBlank @Size(max = 200) String positionTitle,
            @NotNull EmploymentType employmentType,
            WorkModality workModality,
            @Size(max = 200) String location,
            @Pattern(regexp = COUNTRY) String country,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            boolean current,
            @Size(max = 5000) String description,
            @Size(max = 30) List<@NotBlank @Size(max = 500) String> achievements,
            @Size(max = 60) List<@NotNull Long> skillIds,
            Integer version) {

        @AssertTrue(message = "La fecha de fin debe ser posterior o igual a la de inicio")
        public boolean isDateRangeValid() {
            return endDate == null || startDate == null || !endDate.isBefore(startDate);
        }

        @AssertTrue(message = "Una experiencia actual no puede tener fecha de fin")
        public boolean isCurrentConsistent() {
            return !current || endDate == null;
        }
    }

    public record ProjectCommand(
            @NotNull ProjectKind kind,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 150) String role,
            @Size(max = 5000) String description,
            @Pattern(regexp = URL) String url,
            @Pattern(regexp = URL) String repoUrl,
            LocalDate startDate,
            LocalDate endDate,
            @Size(max = 30) List<@NotBlank @Size(max = 500) String> highlights,
            @Size(max = 60) List<@NotNull Long> skillIds,
            Integer version) {

        @AssertTrue(message = "La fecha de fin debe ser posterior o igual a la de inicio")
        public boolean isDateRangeValid() {
            return endDate == null || startDate == null || !endDate.isBefore(startDate);
        }
    }

    public record EducationCommand(
            @NotBlank @Size(max = 200) String institution,
            @Size(max = 200) String degree,
            @Size(max = 200) String fieldOfStudy,
            LocalDate startDate,
            LocalDate endDate,
            EducationStatus status,
            @Size(max = 3000) String description,
            Integer version) {

        @AssertTrue(message = "La fecha de fin debe ser posterior o igual a la de inicio")
        public boolean isDateRangeValid() {
            return endDate == null || startDate == null || !endDate.isBefore(startDate);
        }
    }

    public record CertificationCommand(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 200) String issuer,
            LocalDate issuedAt,
            LocalDate expiresAt,
            @Size(max = 200) String credentialId,
            @Pattern(regexp = URL) String credentialUrl,
            Integer version) {
    }

    public record CatalogSkillCommand(@NotBlank @Size(max = 100) String name, @NotNull SkillCategory category) {
    }

    public record UserSkillCommand(@NotNull EvidenceLevel evidenceLevel, @Min(1) @Max(5) Integer selfRating,
                                   @Min(1970) @Max(2100) Integer lastUsedYear) {
    }

    public record LanguageCommand(@NotNull @Pattern(regexp = LANG) String languageCode, @NotNull LanguageLevel level,
                                  @Size(max = 200) String certificate) {
    }

    public record PreferencesCommand(
            List<@NotNull Region> regions,
            List<@Pattern(regexp = COUNTRY) String> countriesInclude,
            List<@Pattern(regexp = COUNTRY) String> countriesExclude,
            List<@NotNull WorkModality> modalities,
            List<@NotNull ContractType> contractTypes,
            List<@NotNull EngagementType> engagementTypes,
            @DecimalMin("0") BigDecimal minSalary,
            @Pattern(regexp = "^[A-Z]{3}$") String salaryCurrency,
            SalaryPeriod salaryPeriod,
            List<@Pattern(regexp = LANG) String> workLanguages,
            Seniority seniority,
            boolean relocationOk) {
    }

    public record VerifyCommand(List<Long> experienceIds, List<Long> projectIds, List<Long> educationIds,
                                List<Long> certificationIds, List<Long> skillIds, List<String> languageCodes) {
    }

    /** Wrapper so a JSON array body can be validated element by element. */
    public record LanguagesCommand(@Valid List<LanguageCommand> items) {
    }
}
