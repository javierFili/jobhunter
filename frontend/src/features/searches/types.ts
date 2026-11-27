import type { SearchFilters } from '@/features/jobs/types'

export type ScheduleType = 'MANUAL' | 'INTERVAL' | 'DAILY'
export type ExecutionStatus = 'RUNNING' | 'SUCCEEDED' | 'PARTIAL' | 'FAILED'
export type SourceRunStatus =
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'TIMED_OUT'
  | 'ABORTED'
  | 'SKIPPED_BUDGET'
  | 'SKIPPED_DISABLED'
  | 'SKIPPED_INTERVAL'
  | 'CACHED'

export interface SavedSearchRequest {
  name: string
  profileId?: number | null
  keywords?: string | null
  filters: SearchFilters
  sourceCodes: string[]
  scheduleType: ScheduleType
  intervalMinutes?: number | null
  dailyTime?: string | null
  timezone?: string | null
  enabled: boolean
  maxResultsPerSource: number
  notifyOnNew: boolean
  version?: number
}

export interface SavedSearchDto extends Omit<SavedSearchRequest, 'version'> {
  id: number
  nextRunAt: string | null
  lastRunAt: string | null
  lastExecution: null | { id: number; status: ExecutionStatus; jobsFound: number; jobsNew: number; startedAt: string; finishedAt: string | null }
  version: number
}

export interface ExecutionDto {
  id: number
  trigger: 'MANUAL' | 'SCHEDULED'
  status: ExecutionStatus
  startedAt: string
  finishedAt: string | null
  jobsFound: number
  jobsNew: number
  jobsDuplicate: number
  costUsd: number | null
  errorSummary: string | null
}

export interface ExecutionDetailDto extends ExecutionDto {
  sources: {
    sourceCode: string
    status: SourceRunStatus
    requestCount: number
    itemsFetched: number
    itemsNew: number
    itemsDuplicate: number
    costUsd: number | null
    errorMessage: string | null
    startedAt: string | null
    finishedAt: string | null
  }[]
}

export type Ats = 'GREENHOUSE' | 'LEVER' | 'ASHBY'

export interface WatchlistDto {
  id: number
  ats: Ats
  boardToken: string
  companyName: string | null
  enabled: boolean
  lastCheckedAt: string | null
  lastStatus: 'OK' | 'ERROR' | null
  lastJobCount: number | null
}
