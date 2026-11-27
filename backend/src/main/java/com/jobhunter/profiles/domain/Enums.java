package com.jobhunter.profiles.domain;

/** Namespace for the profile module's enumerations. */
public final class Enums {

    private Enums() {
    }

    public enum EmploymentType { FULL_TIME, PART_TIME, CONTRACT, FREELANCE, INTERNSHIP }

    public enum WorkModality { REMOTE, HYBRID, ONSITE }

    /** Projects are NEVER employment. FREELANCE_CLIENT = work for a client outside an employment contract. */
    public enum ProjectKind { PERSONAL, ACADEMIC, FREELANCE_CLIENT, OPEN_SOURCE }

    public enum EducationStatus { COMPLETED, IN_PROGRESS, INCOMPLETE }

    public enum EvidenceLevel { PROFESSIONAL, PROJECT_ONLY, ACADEMIC, LEARNING }

    public enum SkillCategory { PROGRAMMING_LANGUAGE, FRAMEWORK, DATABASE, CLOUD, TOOL, METHODOLOGY, OTHER }

    public enum LanguageLevel { A1, A2, B1, B2, C1, C2, NATIVE }

    public enum ItemSource { MANUAL, CV_IMPORT }

    public enum Region { LATAM, EUROPE, NORTH_AMERICA, ASIA, AFRICA, OCEANIA, WORLDWIDE }

    public enum ContractType { FULL_TIME, PART_TIME, CONTRACT, TEMPORARY, FREELANCE, INTERNSHIP }

    public enum EngagementType { EMPLOYEE_LOCAL, EMPLOYEE_EOR, CONTRACTOR, FREELANCE_PROJECT, SPONSORED_RELOCATION }

    public enum SalaryPeriod { HOUR, DAY, MONTH, YEAR }

    public enum Seniority { JUNIOR, MID, SENIOR }
}
