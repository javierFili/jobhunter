import type { BadgeTone } from '@/components/StatusBadge'
import { contractTypeLabels, engagementTypeLabels } from '@/lib/labels'
import type { Eligibility, JobContractType, JobEngagementType, JobModality, JobSort, JobStatus, RemoteScope, SearchFilters } from './types'

export const eligibilityLabels: Record<Eligibility, string> = {
  ELIGIBLE: 'Accesible desde Bolivia',
  LIKELY: 'Probablemente accesible',
  UNKNOWN: 'Requiere revisión',
  NOT_ELIGIBLE: 'No accesible desde Bolivia',
}

export const eligibilityTone: Record<Eligibility, BadgeTone> = {
  ELIGIBLE: 'success',
  LIKELY: 'teal',
  UNKNOWN: 'neutral',
  NOT_ELIGIBLE: 'danger',
}

export const jobModalityLabels: Record<JobModality, string> = {
  REMOTE: 'Remoto',
  HYBRID: 'Híbrido',
  ONSITE: 'Presencial',
  UNKNOWN: 'Sin especificar',
}

export const remoteScopeLabels: Record<RemoteScope, string> = {
  WORLDWIDE: 'Remoto mundial',
  LATAM: 'Remoto LATAM',
  AMERICAS: 'Remoto Américas',
  EUROPE: 'Remoto Europa',
  COUNTRY_RESTRICTED: 'Restringido a países',
  TIMEZONE_RESTRICTED: 'Restringido por zona horaria',
  UNKNOWN: 'Alcance desconocido',
}

export const jobContractLabels: Record<JobContractType, string> = { ...contractTypeLabels, UNKNOWN: 'Sin especificar' }
export const jobEngagementLabels: Record<JobEngagementType, string> = { ...engagementTypeLabels, UNKNOWN: 'Sin especificar' }

export const jobStatusLabels: Record<JobStatus, string> = {
  ACTIVE: 'Activa',
  EXPIRED: 'Vencida',
  REMOVED: 'Retirada',
  DUPLICATE: 'Duplicada',
}

export const jobSortLabels: Record<JobSort, string> = {
  recent: 'Más recientes (detectadas)',
  published: 'Fecha de publicación',
  salary: 'Salario',
}

export const POSTED_WITHIN_OPTIONS = [
  { value: '1', label: 'Últimas 24 horas' },
  { value: '3', label: 'Últimos 3 días' },
  { value: '7', label: 'Última semana' },
  { value: '14', label: 'Últimas 2 semanas' },
  { value: '30', label: 'Último mes' },
]

export const emptyFilters = (): SearchFilters => ({
  skillIds: [],
  countries: [],
  regions: [],
  modalities: [],
  remoteScopes: [],
  eligibility: [],
  contractTypes: [],
  engagementTypes: [],
  seniority: [],
  salaryMinUsdMonth: null,
  languages: [],
  postedWithinDays: null,
  companies: [],
  excludeCompanies: [],
})
