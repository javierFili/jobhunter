import type { Region, Seniority } from '@/lib/types'
import { emptyFilters } from './labels'
import type { Eligibility, JobContractType, JobEngagementType, JobModality, JobQuery, JobSort, JobStatus, RemoteScope, SearchFilters } from './types'

const list = (v: string | null) => (v ? v.split(',').filter(Boolean) : [])
const num = (v: string | null) => (v && !Number.isNaN(Number(v)) ? Number(v) : null)

export const DEFAULT_PAGE_SIZE = 20

/** URL search params ⇄ JobQuery (the URL is the source of truth for the jobs page). */
export function parseJobQuery(params: URLSearchParams): JobQuery {
  const f = emptyFilters()
  f.skillIds = list(params.get('skillIds')).map(Number).filter((n) => !Number.isNaN(n))
  f.countries = list(params.get('countries'))
  f.regions = list(params.get('regions')) as Region[]
  f.modalities = list(params.get('modalities')) as JobModality[]
  f.remoteScopes = list(params.get('remoteScopes')) as RemoteScope[]
  f.eligibility = list(params.get('eligibility')) as Eligibility[]
  f.contractTypes = list(params.get('contractTypes')) as JobContractType[]
  f.engagementTypes = list(params.get('engagementTypes')) as JobEngagementType[]
  f.seniority = list(params.get('seniority')) as Seniority[]
  f.salaryMinUsdMonth = num(params.get('salaryMinUsdMonth'))
  f.languages = list(params.get('languages'))
  f.postedWithinDays = num(params.get('postedWithinDays'))
  return {
    q: params.get('q') ?? '',
    filters: f,
    company: params.get('company') ?? '',
    sources: list(params.get('sources')),
    saved: params.get('saved') === 'true',
    includeHidden: params.get('includeHidden') === 'true',
    status: (params.get('status') as JobStatus | null) ?? '',
    sort: (params.get('sort') as JobSort | null) ?? 'recent',
    page: num(params.get('page')) ?? 0,
    size: num(params.get('size')) ?? DEFAULT_PAGE_SIZE,
  }
}

/** Flat query object for GET /jobs (arrays comma-joined, empty values dropped). */
export function toApiQuery(q: JobQuery): Record<string, string | number | boolean | undefined> {
  const join = (a: (string | number)[]) => (a.length ? a.join(',') : undefined)
  const f = q.filters
  return {
    q: q.q || undefined,
    skillIds: join(f.skillIds),
    countries: join(f.countries),
    regions: join(f.regions),
    modalities: join(f.modalities),
    remoteScopes: join(f.remoteScopes),
    eligibility: join(f.eligibility),
    contractTypes: join(f.contractTypes),
    engagementTypes: join(f.engagementTypes),
    seniority: join(f.seniority),
    salaryMinUsdMonth: f.salaryMinUsdMonth ?? undefined,
    languages: join(f.languages),
    postedWithinDays: f.postedWithinDays ?? undefined,
    company: q.company || undefined,
    sources: join(q.sources),
    saved: q.saved || undefined,
    includeHidden: q.includeHidden || undefined,
    status: q.status || undefined,
    sort: q.sort,
    page: q.page,
    size: q.size,
  }
}

export function toSearchParams(q: JobQuery): URLSearchParams {
  const p = new URLSearchParams()
  for (const [k, v] of Object.entries(toApiQuery(q))) {
    if (v === undefined || v === '') continue
    if (k === 'sort' && v === 'recent') continue
    if (k === 'page' && v === 0) continue
    if (k === 'size' && v === DEFAULT_PAGE_SIZE) continue
    p.set(k, String(v))
  }
  return p
}

export function countActiveFilters(f: SearchFilters): number {
  return (
    [f.skillIds, f.countries, f.regions, f.modalities, f.remoteScopes, f.eligibility, f.contractTypes, f.engagementTypes, f.seniority, f.languages, f.companies, f.excludeCompanies].filter(
      (a) => a.length > 0,
    ).length + (f.salaryMinUsdMonth != null ? 1 : 0) + (f.postedWithinDays != null ? 1 : 0)
  )
}
