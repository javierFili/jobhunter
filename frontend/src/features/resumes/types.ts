import type {
  CertificationRequest,
  EducationRequest,
  ExperienceRequest,
  LanguageLevel,
  LanguageRequest,
  ProjectKind,
  ProjectRequest,
} from '@/lib/types'

// ---------- Uploads / import ----------
export type UploadStatus = 'SUCCEEDED' | 'FAILED'

export interface UploadSummaryDto {
  id: number
  extractor: 'PDFBOX' | 'POI'
  parser: 'HEURISTIC' | 'AI'
  status: UploadStatus
  createdAt: string
  importedAt: string | null
}

export type ResumeSection = 'SUMMARY' | 'EXPERIENCE' | 'EDUCATION' | 'PROJECTS' | 'SKILLS' | 'LANGUAGES' | 'CERTIFICATIONS' | 'OTHER'

export interface ParsedResume {
  contact: {
    fullName: string | null
    email: string | null
    phone: string | null
    githubUrl: string | null
    linkedinUrl: string | null
    websiteUrl: string | null
    location: string | null
  } | null
  summary: string | null
  experiences: {
    positionTitle: string | null
    companyName: string | null
    startDate: string | null
    endDate: string | null
    current: boolean | null
    description: string | null
    achievements: string[] | null
    skillIds: number[] | null
    rawText: string | null
  }[] | null
  educations: { institution: string | null; degree: string | null; startDate: string | null; endDate: string | null; rawText: string | null }[] | null
  projects: { name: string | null; description: string | null; skillIds: number[] | null; rawText: string | null }[] | null
  certifications: { name: string | null; issuer: string | null; issuedAt: string | null; rawText: string | null }[] | null
  skills: { skillId: number; name: string }[] | null
  languages: { languageCode: string | null; level: LanguageLevel | null; rawText: string | null }[] | null
  sections: Partial<Record<ResumeSection, string>> | null
  warnings: string[] | null
}

export interface UploadDto extends UploadSummaryDto {
  rawText: string | null
  parsed: ParsedResume | null
}

export interface ImportRequest {
  experiences: ExperienceRequest[]
  projects: ProjectRequest[]
  educations: EducationRequest[]
  certifications: CertificationRequest[]
  skillIds: number[]
  languages: LanguageRequest[]
}

export interface ImportResult {
  experiences: number
  projects: number
  educations: number
  certifications: number
  skills: number
  languages: number
}

// ---------- Resumes ----------
export type ResumeLanguage = 'es' | 'en' | 'it' | 'de' | 'fr'
export type TemplateId = 'classic' | 'modern'

export interface ResumeTemplate {
  id: TemplateId
  name: string
  description: string
}

export interface ResumeDto {
  id: number
  profileId: number | null
  title: string
  language: ResumeLanguage
  templateId: TemplateId
  currentVersionId: number | null
  updatedAt: string
  version: number
}

export type VersionOrigin = 'UPLOAD' | 'PROFILE' | 'MANUAL' | 'OPTIMIZED' | 'TRANSLATED'
export type VersionStatus = 'DRAFT' | 'APPROVED' | 'ARCHIVED'

export interface VersionSummaryDto {
  id: number
  versionNo: number
  parentVersionId: number | null
  origin: VersionOrigin
  jobOfferId: number | null
  language: ResumeLanguage
  status: VersionStatus
  createdAt: string
  approvedAt: string | null
  hasPdf: boolean
  note: string | null
}

export interface CreateResumeRequest {
  profileId?: number | null
  title: string
  language: ResumeLanguage
  templateId: TemplateId
}

export interface CreateResumeResponse {
  resume: ResumeDto
  version: VersionSummaryDto
  unverifiedItemsSkipped: number
}

export type BulletStatus = 'GROUNDED' | 'SUGGESTED' | 'USER_EDITED'

export interface Bullet {
  text: string
  sourceRefs: string[]
  status: BulletStatus
  /** Why a bullet is SUGGESTED (e.g. mentions a technology not in the verified profile). */
  note?: string | null
}

export type ViolationRule =
  | 'UNKNOWN_REF'
  | 'UNVERIFIED_TECHNOLOGY'
  | 'UNSUPPORTED_NUMBER'
  | 'YEARS_EXCEEDED'
  | 'PROJECT_AS_EMPLOYMENT'
  | 'NO_SOURCE'
  | 'DROPPED_ENTRY'

export interface Violation {
  rule: ViolationRule
  section: string
  ref: string | null
  text: string
  message: string
}

export interface ValidationReport {
  provider?: string | null
  model?: string | null
  jobOfferId?: number | null
  groundedCount: number
  suggestedCount: number
  violations: Violation[]
}

export interface ResumeDocument {
  basics: {
    fullName: string | null
    headline: string | null
    email: string | null
    phone: string | null
    location: string | null
    links: { label: string; url: string }[]
  }
  summary: string | null
  experience: {
    ref: string
    company: string | null
    title: string | null
    location: string | null
    startDate: string | null
    endDate: string | null
    current: boolean
    bullets: Bullet[]
  }[]
  projects: { ref: string; kind: ProjectKind | null; name: string | null; role: string | null; url: string | null; bullets: Bullet[] }[]
  education: {
    ref: string
    institution: string | null
    degree: string | null
    field: string | null
    startDate: string | null
    endDate: string | null
    status: string | null
  }[]
  certifications: { ref: string; name: string | null; issuer: string | null; date: string | null; url: string | null }[]
  skills: { label: string; items: { ref: string; name: string; evidence: string | null }[] }[]
  languages: { ref: string; code: string; level: string | null }[]
}

export interface VersionDetailDto {
  summary: VersionSummaryDto
  content: ResumeDocument
  validationReport: ValidationReport | null
}

export type DiffSection = 'basics' | 'summary' | 'experience' | 'projects' | 'education' | 'certifications' | 'skills' | 'languages'

export interface DiffLine {
  op: 'SAME' | 'ADDED' | 'REMOVED'
  section: DiffSection
  text: string
}
