// DTOs mirroring the backend contract (/api/v1, camelCase JSON).

export type Role = 'USER' | 'ADMIN'
export type Theme = 'light' | 'dark' | 'system'

export interface UserDto {
  id: number
  email: string
  fullName: string
  role: Role
  locale: string
  timezone: string
  theme: Theme
}

export interface AuthResponse {
  accessToken: string
  expiresIn: number
  user: UserDto
}

export type ConsentType = 'AI_PROCESS_PROFILE' | 'AI_PROCESS_CV'
export type AiProvider = 'deepseek' | 'claude'

export interface ConsentDto {
  consentType: ConsentType
  provider: AiProvider
  granted: boolean
  grantedAt: string | null
}

export interface SkillRef {
  id: number
  name: string
}

export interface ProfileDto {
  id: number
  name: string
  headline: string | null
  summary: string | null
  targetRole: string | null
  isDefault: boolean
  contactEmail: string | null
  phone: string | null
  city: string | null
  country: string | null
  githubUrl: string | null
  linkedinUrl: string | null
  portfolioUrl: string | null
  websiteUrl: string | null
  experienceIds: number[]
  projectIds: number[]
  skillIds: number[]
  version: number
}

export type ProfileRequest = Omit<ProfileDto, 'id' | 'experienceIds' | 'projectIds' | 'skillIds' | 'version'> & {
  version?: number
}

export interface ProfileItemsRequest {
  experienceIds: number[]
  projectIds: number[]
  skillIds: number[]
}

export type EmploymentType = 'FULL_TIME' | 'PART_TIME' | 'CONTRACT' | 'FREELANCE' | 'INTERNSHIP'
export type WorkModality = 'REMOTE' | 'HYBRID' | 'ONSITE'

export interface ExperienceDto {
  id: number
  companyName: string
  companyUrl: string | null
  positionTitle: string
  employmentType: EmploymentType
  workModality: WorkModality
  location: string | null
  country: string | null
  startDate: string
  endDate: string | null
  current: boolean
  description: string | null
  achievements: string[]
  skills: SkillRef[]
  verified: boolean
  source: 'MANUAL' | 'CV_IMPORT'
  version: number
}

export type ExperienceRequest = Omit<ExperienceDto, 'id' | 'source' | 'verified' | 'skills' | 'version'> & {
  skillIds: number[]
  version?: number
}

export type ProjectKind = 'PERSONAL' | 'ACADEMIC' | 'FREELANCE_CLIENT' | 'OPEN_SOURCE'

export interface ProjectDto {
  id: number
  kind: ProjectKind
  name: string
  role: string | null
  description: string | null
  url: string | null
  repoUrl: string | null
  startDate: string | null
  endDate: string | null
  highlights: string[]
  skills: SkillRef[]
  verified: boolean
  version: number
}

export type ProjectRequest = Omit<ProjectDto, 'id' | 'verified' | 'skills' | 'version'> & {
  skillIds: number[]
  version?: number
}

export type EducationStatus = 'COMPLETED' | 'IN_PROGRESS' | 'INCOMPLETE'

export interface EducationDto {
  id: number
  institution: string
  degree: string | null
  fieldOfStudy: string | null
  startDate: string | null
  endDate: string | null
  status: EducationStatus
  description: string | null
  verified: boolean
  version: number
}

export type EducationRequest = Omit<EducationDto, 'id' | 'verified' | 'version'> & { version?: number }

export interface CertificationDto {
  id: number
  name: string
  issuer: string | null
  issuedAt: string | null
  expiresAt: string | null
  credentialId: string | null
  credentialUrl: string | null
  verified: boolean
  version: number
}

export type CertificationRequest = Omit<CertificationDto, 'id' | 'verified' | 'version'> & { version?: number }

export interface VerifyRequest {
  experienceIds: number[]
  projectIds: number[]
  educationIds: number[]
  certificationIds: number[]
  skillIds: number[]
  languageCodes: string[]
}

export type SkillCategory =
  | 'PROGRAMMING_LANGUAGE'
  | 'FRAMEWORK'
  | 'DATABASE'
  | 'CLOUD'
  | 'TOOL'
  | 'METHODOLOGY'
  | 'OTHER'

export interface CatalogSkill {
  id: number
  name: string
  category: SkillCategory
}

export type EvidenceLevel = 'PROFESSIONAL' | 'PROJECT_ONLY' | 'ACADEMIC' | 'LEARNING'

export interface UserSkillDto {
  skillId: number
  name: string
  category: SkillCategory
  evidenceLevel: EvidenceLevel
  selfRating: number | null
  lastUsedYear: number | null
  yearsProfessional: number
  verified: boolean
}

export interface UserSkillRequest {
  evidenceLevel: EvidenceLevel
  selfRating?: number | null
  lastUsedYear?: number | null
}

export type LanguageLevel = 'A1' | 'A2' | 'B1' | 'B2' | 'C1' | 'C2' | 'NATIVE'

export interface LanguageDto {
  languageCode: string
  level: LanguageLevel
  certificate: string | null
  verified: boolean
}

export interface LanguageRequest {
  languageCode: string
  level: LanguageLevel
  certificate?: string | null
}

export type Region = 'LATAM' | 'EUROPE' | 'NORTH_AMERICA' | 'ASIA' | 'AFRICA' | 'OCEANIA' | 'WORLDWIDE'
export type ContractType = 'FULL_TIME' | 'PART_TIME' | 'CONTRACT' | 'TEMPORARY' | 'FREELANCE' | 'INTERNSHIP'
export type EngagementType =
  | 'EMPLOYEE_LOCAL'
  | 'EMPLOYEE_EOR'
  | 'CONTRACTOR'
  | 'FREELANCE_PROJECT'
  | 'SPONSORED_RELOCATION'
export type SalaryPeriod = 'HOUR' | 'DAY' | 'MONTH' | 'YEAR'
export type Seniority = 'JUNIOR' | 'MID' | 'SENIOR'

export interface PreferencesDto {
  regions: Region[]
  countriesInclude: string[]
  countriesExclude: string[]
  modalities: WorkModality[]
  contractTypes: ContractType[]
  engagementTypes: EngagementType[]
  minSalary: number | null
  salaryCurrency: string | null
  salaryPeriod: SalaryPeriod | null
  workLanguages: string[]
  seniority: Seniority | null
  relocationOk: boolean
}

export type MissingSection = 'headline' | 'summary' | 'experience' | 'skills' | 'languages' | 'preferences' | 'education'

export interface DashboardSummary {
  profileCompleteness: number
  missingSections: MissingSection[]
  counts: {
    profiles: number
    experiences: number
    projects: number
    educations: number
    certifications: number
    skills: number
    languages: number
  }
  /** Phase 3+: present when job data exists. */
  jobs?: { total: number; newLast7Days: number; eligibleOrLikely: number; saved: number }
  searches?: { count: number; lastRunAt: string | null }
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
