import type { TaskStateDto } from '@/lib/task'
import type { SalaryPeriod } from '@/lib/types'
import type { Eligibility } from '@/features/jobs/types'

export type AiProviderCode = 'DEEPSEEK' | 'CLAUDE'

export interface AnalysisOutput {
  summary: string | null
  seniority: string | null
  minYearsExperience: number | null
  mandatorySkills: { name: string; minYears: number | null }[]
  preferredSkills: { name: string; minYears: number | null }[]
  languages: { code: string; level: string | null; mandatory: boolean }[]
  salary: { min: number | null; max: number | null; currency: string | null; period: SalaryPeriod | null } | null
  location: { scope: string | null; countries: string[]; boliviaEligibility: Eligibility | null; evidenceQuote: string | null } | null
  engagementType: string | null
  contractType: string | null
  injectionSuspected: boolean
}

export interface AnalysisDto {
  id: number
  status: 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'INVALID_OUTPUT' | 'REFUSED'
  provider: AiProviderCode
  model: string
  createdAt: string
  costUsd: number | null
  inputTokens: number | null
  outputTokens: number | null
  output: AnalysisOutput | null
  error: string | null
}

export interface RequirementDto {
  kind: string
  importance: 'MANDATORY' | 'PREFERRED'
  skillId: number | null
  text: string
  minYears: number | null
  languageCode: string | null
  level: string | null
  extractedBy: 'AI' | 'DICTIONARY'
}

export interface JobAnalysisResponse {
  task: TaskStateDto
  analysis: AnalysisDto | null
  requirements: RequirementDto[]
}

export type Classification = 'HIGH_MATCH' | 'MEDIUM_MATCH' | 'LOW_MATCH' | 'NOT_ELIGIBLE' | 'NEEDS_REVIEW'
export type Recommendation = 'APPLY' | 'CONSIDER' | 'SKIP' | 'REVIEW'
export type ScoreDimension = 'skills' | 'experience' | 'location' | 'language' | 'seniority' | 'compensation' | 'contract'

export interface MatchDto {
  jobOfferId: number
  scoreTotal: number
  classification: Classification
  recommendation: Recommendation
  method: 'DETERMINISTIC' | 'AI_ASSISTED'
  scores: Record<ScoreDimension, number | null>
  reasons: { name: string; score: number | null; reason: string }[]
  strengths: string[]
  gaps: string[]
  updatedAt: string
}

export interface JobMatchResponse {
  task: TaskStateDto
  match: MatchDto | null
}
