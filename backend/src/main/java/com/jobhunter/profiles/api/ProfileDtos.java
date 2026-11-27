package com.jobhunter.profiles.api;

import com.jobhunter.profiles.domain.Certification;
import com.jobhunter.profiles.domain.Education;
import com.jobhunter.profiles.domain.Experience;
import com.jobhunter.profiles.domain.JobPreferences;
import com.jobhunter.profiles.domain.ProfessionalProfile;
import com.jobhunter.profiles.domain.Project;
import com.jobhunter.profiles.domain.Skill;
import com.jobhunter.profiles.domain.UserLanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Response DTOs of the profile module. */
public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record SkillRef(Long id, String name) {
    }

    public record CatalogSkillDto(Long id, String name, String category) {
        public static CatalogSkillDto from(Skill s) {
            return new CatalogSkillDto(s.getId(), s.getName(), s.getCategory().name());
        }
    }

    static List<SkillRef> refs(Collection<Long> ids, Map<Long, Skill> catalogue) {
        return ids.stream().map(catalogue::get).filter(Objects::nonNull)
                .map(s -> new SkillRef(s.getId(), s.getName())).toList();
    }

    public record ProfileDto(Long id, String name, String headline, String summary, String targetRole,
                             boolean isDefault, String contactEmail, String phone, String city, String country,
                             String githubUrl, String linkedinUrl, String portfolioUrl, String websiteUrl,
                             List<Long> experienceIds, List<Long> projectIds, List<Long> skillIds, int version) {
        public static ProfileDto from(ProfessionalProfile p) {
            return new ProfileDto(p.getId(), p.getName(), p.getHeadline(), p.getSummary(), p.getTargetRole(),
                    p.isDefault(), p.getContactEmail(), p.getPhone(), p.getCity(), p.getCountry(), p.getGithubUrl(),
                    p.getLinkedinUrl(), p.getPortfolioUrl(), p.getWebsiteUrl(), List.copyOf(p.getExperienceIds()),
                    List.copyOf(p.getProjectIds()), List.copyOf(p.getSkillIds()), p.getVersion());
        }
    }

    public record ExperienceDto(Long id, String companyName, String companyUrl, String positionTitle,
                                String employmentType, String workModality, String location, String country,
                                LocalDate startDate, LocalDate endDate, boolean current, String description,
                                List<String> achievements, List<SkillRef> skills, boolean verified, String source,
                                int version) {
        public static ExperienceDto from(Experience e, Map<Long, Skill> catalogue) {
            return new ExperienceDto(e.getId(), e.getCompanyName(), e.getCompanyUrl(), e.getPositionTitle(),
                    e.getEmploymentType().name(), e.getWorkModality() == null ? null : e.getWorkModality().name(),
                    e.getLocation(), e.getCountry(), e.getStartDate(), e.getEndDate(), e.isCurrent(),
                    e.getDescription(), List.copyOf(e.getAchievements()), refs(e.getSkillIds(), catalogue),
                    e.isVerified(), e.getSource().name(), e.getVersion());
        }
    }

    public record ProjectDto(Long id, String kind, String name, String role, String description, String url,
                             String repoUrl, LocalDate startDate, LocalDate endDate, List<String> highlights,
                             List<SkillRef> skills, boolean verified, int version) {
        public static ProjectDto from(Project p, Map<Long, Skill> catalogue) {
            return new ProjectDto(p.getId(), p.getKind().name(), p.getName(), p.getRole(), p.getDescription(),
                    p.getUrl(), p.getRepoUrl(), p.getStartDate(), p.getEndDate(), List.copyOf(p.getHighlights()),
                    refs(p.getSkillIds(), catalogue), p.isVerified(), p.getVersion());
        }
    }

    public record EducationDto(Long id, String institution, String degree, String fieldOfStudy, LocalDate startDate,
                               LocalDate endDate, String status, String description, boolean verified, int version) {
        public static EducationDto from(Education e) {
            return new EducationDto(e.getId(), e.getInstitution(), e.getDegree(), e.getFieldOfStudy(),
                    e.getStartDate(), e.getEndDate(), e.getStatus().name(), e.getDescription(), e.isVerified(),
                    e.getVersion());
        }
    }

    public record CertificationDto(Long id, String name, String issuer, LocalDate issuedAt, LocalDate expiresAt,
                                   String credentialId, String credentialUrl, boolean verified, int version) {
        public static CertificationDto from(Certification c) {
            return new CertificationDto(c.getId(), c.getName(), c.getIssuer(), c.getIssuedAt(), c.getExpiresAt(),
                    c.getCredentialId(), c.getCredentialUrl(), c.isVerified(), c.getVersion());
        }
    }

    public record LanguageDto(String languageCode, String level, String certificate, boolean verified) {
        public static LanguageDto from(UserLanguage l) {
            return new LanguageDto(l.getLanguageCode(), l.getLevel().name(), l.getCertificate(), l.isVerified());
        }
    }

    public record PreferencesDto(List<String> regions, List<String> countriesInclude, List<String> countriesExclude,
                                 List<String> modalities, List<String> contractTypes, List<String> engagementTypes,
                                 BigDecimal minSalary, String salaryCurrency, String salaryPeriod,
                                 List<String> workLanguages, String seniority, boolean relocationOk) {
        public static PreferencesDto from(JobPreferences p) {
            return new PreferencesDto(p.getRegions(), p.getCountriesInclude(), p.getCountriesExclude(),
                    p.getModalities(), p.getContractTypes(), p.getEngagementTypes(), p.getMinSalary(),
                    p.getSalaryCurrency(), p.getSalaryPeriod() == null ? null : p.getSalaryPeriod().name(),
                    p.getWorkLanguages(), p.getSeniority() == null ? null : p.getSeniority().name(),
                    p.isRelocationOk());
        }
    }
}
