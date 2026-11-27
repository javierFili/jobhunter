import type { BadgeTone } from '@/components/StatusBadge'
import { emptyFilters } from '@/features/jobs/labels'
import type { Ats, ExecutionStatus, SavedSearchRequest, ScheduleType, SourceRunStatus } from './types'

export const scheduleLabels: Record<ScheduleType, string> = {
  MANUAL: 'Manual',
  INTERVAL: 'Cada cierto tiempo',
  DAILY: 'Diaria',
}

export const executionStatusLabels: Record<ExecutionStatus, string> = {
  RUNNING: 'En curso',
  SUCCEEDED: 'Completada',
  PARTIAL: 'Parcial',
  FAILED: 'Fallida',
}

export const executionStatusTone: Record<ExecutionStatus, BadgeTone> = {
  RUNNING: 'info',
  SUCCEEDED: 'success',
  PARTIAL: 'warning',
  FAILED: 'danger',
}

export const sourceRunLabels: Record<SourceRunStatus, string> = {
  RUNNING: 'En curso',
  SUCCEEDED: 'Correcta',
  FAILED: 'Error',
  TIMED_OUT: 'Tiempo agotado',
  ABORTED: 'Cancelada',
  SKIPPED_BUDGET: 'Omitida: presupuesto agotado',
  SKIPPED_DISABLED: 'Omitida: fuente desactivada',
  SKIPPED_INTERVAL: 'Omitida: intervalo mínimo',
  CACHED: 'Desde caché',
}

export const sourceRunTone: Record<SourceRunStatus, BadgeTone> = {
  RUNNING: 'info',
  SUCCEEDED: 'success',
  FAILED: 'danger',
  TIMED_OUT: 'danger',
  ABORTED: 'neutral',
  SKIPPED_BUDGET: 'warning',
  SKIPPED_DISABLED: 'neutral',
  SKIPPED_INTERVAL: 'neutral',
  CACHED: 'teal',
}

export const atsLabels: Record<Ats, string> = { GREENHOUSE: 'Greenhouse', LEVER: 'Lever', ASHBY: 'Ashby' }

export const atsBoardUrl: Record<Ats, string> = {
  GREENHOUSE: 'job-boards.greenhouse.io/',
  LEVER: 'jobs.lever.co/',
  ASHBY: 'jobs.ashbyhq.com/',
}

export const BOARD_TOKEN_RE = /^[A-Za-z0-9_.-]{1,100}$/

export const defaultSearchRequest = (): SavedSearchRequest => ({
  name: '',
  profileId: null,
  keywords: '',
  filters: emptyFilters(),
  sourceCodes: [],
  scheduleType: 'DAILY',
  intervalMinutes: 360,
  dailyTime: '08:00',
  timezone: 'America/La_Paz',
  enabled: true,
  maxResultsPerSource: 50,
  notifyOnNew: true,
})

/** Quick templates for the "Nueva búsqueda" dialog (prefill only). */
export const SEARCH_TEMPLATES: { name: string; apply: (r: SavedSearchRequest) => SavedSearchRequest }[] = [
  {
    name: 'Laravel remoto mundial',
    apply: (r) => ({ ...r, name: 'Laravel remoto mundial', keywords: 'Laravel', filters: { ...r.filters, modalities: ['REMOTE'], remoteScopes: ['WORLDWIDE'] } }),
  },
  {
    name: 'Java junior Europa',
    apply: (r) => ({ ...r, name: 'Java junior Europa', keywords: 'Java', filters: { ...r.filters, regions: ['EUROPE'], seniority: ['JUNIOR'] } }),
  },
  {
    name: 'Backend LATAM',
    apply: (r) => ({
      ...r,
      name: 'Backend LATAM',
      keywords: 'backend',
      filters: { ...r.filters, regions: ['LATAM'], remoteScopes: ['LATAM', 'AMERICAS', 'WORLDWIDE'], eligibility: ['ELIGIBLE', 'LIKELY'] },
    }),
  },
  {
    name: 'Proyectos freelance Laravel',
    apply: (r) => ({
      ...r,
      name: 'Proyectos freelance Laravel',
      keywords: 'Laravel',
      filters: { ...r.filters, contractTypes: ['FREELANCE', 'CONTRACT'], engagementTypes: ['FREELANCE_PROJECT', 'CONTRACTOR'] },
    }),
  },
  {
    name: 'Spring Boot junior remoto',
    apply: (r) => ({ ...r, name: 'Spring Boot junior remoto', keywords: 'Spring Boot', filters: { ...r.filters, modalities: ['REMOTE'], seniority: ['JUNIOR'] } }),
  },
]
