import type { ContractType, EngagementType, Region, SalaryPeriod, Seniority, SkillRef } from '@/lib/types'

export type Eligibility = 'ELIGIBLE' | 'LIKELY' | 'NOT_ELIGIBLE' | 'UNKNOWN'
export type JobModality = 'REMOTE' | 'HYBRID' | 'ONSITE' | 'UNKNOWN'
export type RemoteScope = 'WORLDWIDE' | 'LATAM' | 'AMERICAS' | 'EUROPE' | 'COUNTRY_RESTRICTED' | 'TIMEZONE_RESTRICTED' | 'UNKNOWN'
export type JobContractType = ContractType | 'UNKNOWN'
export type JobEngagementType = EngagementType | 'UNKNOWN'
export type JobStatus = 'ACTIVE' | 'EXPIRED' | 'REMOVED' | 'DUPLICATE'
export type JobSort = 'recent' | 'published' | 'salary'

export interface JobSourceRef {
  code: string
  displayName: string
  attributionText: string
  sourceUrl: string | null
}

export interface JobSummaryDto {
  id: number
  title: string
  companyName: string | null
  locationText: string | null
  country: string | null
  region: Region | null
  workModality: JobModality | null
  remoteScope: RemoteScope | null
  boliviaEligibility: Eligibility
  engagementType: JobEngagementType | null
  contractType: JobContractType | null
  seniority: Seniority | null
  salaryMin: number | null
  salaryMax: number | null
  salaryCurrency: string | null
  salaryPeriod: SalaryPeriod | null
  salaryIsEstimated: boolean
  salaryText: string | null
  salaryMinUsdMonth: number | null
  publishedAt: string | null
  publishedIsApprox: boolean
  firstSeenAt: string
  status: JobStatus
  skills: SkillRef[]
  sources: JobSourceRef[]
  saved: boolean
  hidden: boolean
  match: null | { scoreTotal: number; classification: 'HIGH_MATCH' | 'MEDIUM_MATCH' | 'LOW_MATCH' | 'NOT_ELIGIBLE' | 'NEEDS_REVIEW' }
}

export interface JobReference {
  sourceCode: string
  sourceName: string
  sourceUrl: string | null
  applyUrl: string | null
  attributionText: string
  firstSeenAt: string
  lastSeenAt: string | null
  removedAt: string | null
}

export interface JobDetailDto extends JobSummaryDto {
  descriptionHtml: string | null
  descriptionText: string | null
  eligibilityEvidence: string | null
  remoteCountries: string[]
  requiredLanguages: { code: string; level: string | null; evidence: string | null }[]
  applyUrl: string | null
  canonicalUrl: string | null
  expiresAt: string | null
  lastSeenAt: string | null
  lastVerifiedAt: string | null
  references: JobReference[]
}

/** Filters shared by GET /jobs and saved searches. */
export interface SearchFilters {
  skillIds: number[]
  countries: string[]
  regions: Region[]
  modalities: JobModality[]
  remoteScopes: RemoteScope[]
  eligibility: Eligibility[]
  contractTypes: JobContractType[]
  engagementTypes: JobEngagementType[]
  seniority: Seniority[]
  salaryMinUsdMonth: number | null
  languages: string[]
  postedWithinDays: number | null
  companies: string[]
  excludeCompanies: string[]
}

export interface JobQuery {
  q: string
  filters: SearchFilters
  company: string
  sources: string[]
  saved: boolean
  includeHidden: boolean
  status: JobStatus | ''
  sort: JobSort
  page: number
  size: number
}

export interface SourceDto {
  code: string
  type: 'PUBLIC_API' | 'ATS_BOARD' | 'SEARCH_API' | 'APIFY_ACTOR'
  displayName: string
  integrationCode: string | null
  usable: boolean
  reason: string | null
  capabilities: { keyword: boolean; country: boolean; salary: boolean; postedSince: boolean; remoteOnly: boolean }
  attributionText: string
  termsUrl: string | null
  termsNotes: string | null
}
