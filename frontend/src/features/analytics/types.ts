import type { EvidenceLevel } from '@/lib/types'
import type { Classification } from '@/features/matching/types'
import type { TaskStateDto } from '@/lib/task'

export interface OverviewDto {
  jobs: { total: number; active: number; newInPeriod: number; remote: number; freelance: number; eligibleOrLikely: number }
  applications: { total: number; applied: number; responses: number; interviews: number; offers: number; rejections: number; responseRate: number | null }
  matches: Record<Classification, number>
  usage: { aiCostUsd: number; apifyCostUsd: number; totalRequests: number }
}

export type BreakdownDimension = 'country' | 'region' | 'source' | 'technology' | 'modality' | 'salaryBand' | 'engagement' | 'eligibility'

export interface BreakdownRow {
  key: string
  label: string
  count: number
}

export interface TimelinePoint {
  date: string
  newJobs: number
  eligibleOrLikely: number
}

export interface FunnelStage {
  stage: string
  count: number
}

export interface SkillDemandRow {
  skillId: number
  name: string
  jobs: number
  share: number
  userEvidence: EvidenceLevel | null
}

export interface SkillRecommendations {
  task: TaskStateDto
  result: null | {
    generatedAt: string
    provider: string
    model: string
    basedOnJobs: number
    recommendations: { skill: string; priority: 'HIGH' | 'MEDIUM' | 'LOW'; reason: string }[]
    disclaimer: string
  }
}

export interface DailyUsageRow {
  date: string
  code: string
  requests: number
  costUsd: number
}
