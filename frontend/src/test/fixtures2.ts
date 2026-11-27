// ⚠️ TEST DATA — fictitious Phase 2/3 fixtures used only by automated tests (MSW).
import type { IntegrationDto, AdminSourceDto, FxRateDto } from '@/features/admin/types'
import type { JobDetailDto, JobSummaryDto, SourceDto } from '@/features/jobs/types'
import type { NotificationDto } from '@/features/notifications/api'
import type { ResumeDocument, ResumeDto, UploadDto, VersionSummaryDto } from '@/features/resumes/types'
import type { SavedSearchDto, WatchlistDto } from '@/features/searches/types'
import { emptyFilters } from '@/features/jobs/labels'
import type { AnalysisDto, MatchDto, RequirementDto } from '@/features/matching/types'
import type { AiRouteDto, PromptVersionDto } from '@/features/admin/types'

export const upload: UploadDto = {
  id: 77,
  extractor: 'PDFBOX',
  parser: 'HEURISTIC',
  status: 'SUCCEEDED',
  createdAt: '2026-11-20T10:00:00Z',
  importedAt: null,
  rawText: 'TEST DATA raw text',
  parsed: {
    contact: { fullName: 'TEST DATA Persona', email: 'test@example.test', phone: null, githubUrl: null, linkedinUrl: null, websiteUrl: null, location: 'La Paz' },
    summary: 'TEST DATA resumen',
    experiences: [
      {
        positionTitle: 'TEST DATA Desarrollador',
        companyName: 'TEST DATA — Example Corp',
        startDate: '2020-03',
        endDate: '2022-06-30',
        current: false,
        description: 'TEST DATA descripción',
        achievements: ['TEST DATA logro'],
        skillIds: [101],
        rawText: 'TEST DATA Desarrollador — Example Corp 2020-2022',
      },
    ],
    educations: [{ institution: 'TEST DATA Universidad', degree: 'Licenciatura', startDate: '2014', endDate: '2019', rawText: 'TEST DATA Universidad 2014-2019' }],
    projects: [{ name: 'TEST DATA Proyecto', description: null, skillIds: [], rawText: 'TEST DATA Proyecto' }],
    certifications: [],
    skills: [
      { skillId: 101, name: 'TEST DATA Java' },
      { skillId: 102, name: 'TEST DATA React' },
    ],
    languages: [{ languageCode: 'en', level: null, rawText: 'Inglés intermedio' }],
    sections: {},
    warnings: ['TEST DATA: no se pudo leer la sección de certificaciones'],
  },
}

const bullet = (text: string, status: 'GROUNDED' | 'SUGGESTED' | 'USER_EDITED' = 'GROUNDED') => ({ text, sourceRefs: ['exp:1'], status })

export const resumeDoc: ResumeDocument = {
  basics: { fullName: 'TEST DATA Persona', headline: 'Backend Developer', email: 'test@example.test', phone: null, location: 'La Paz', links: [] },
  summary: 'TEST DATA resumen',
  experience: [
    {
      ref: 'exp:1',
      company: 'TEST DATA — Example Corp',
      title: 'Backend Developer',
      location: 'Remoto',
      startDate: '2021-03',
      endDate: null,
      current: true,
      bullets: [bullet('TEST DATA viñeta basada en perfil'), bullet('TEST DATA viñeta sugerida', 'SUGGESTED')],
    },
  ],
  projects: [],
  education: [],
  certifications: [],
  skills: [{ label: 'Backend', items: [{ ref: 'skill:101', name: 'TEST DATA Java', evidence: 'PROFESSIONAL' }] }],
  languages: [{ ref: 'lang:es', code: 'es', level: 'NATIVE' }],
}

export const resume: ResumeDto = {
  id: 5,
  profileId: null,
  title: 'TEST DATA CV Backend',
  language: 'es',
  templateId: 'classic',
  currentVersionId: 52,
  updatedAt: '2026-11-21T10:00:00Z',
  version: 0,
}

export const versions: VersionSummaryDto[] = [
  { id: 52, versionNo: 2, parentVersionId: 51, origin: 'MANUAL', jobOfferId: null, language: 'es', status: 'DRAFT', createdAt: '2026-11-21T10:00:00Z', approvedAt: null, hasPdf: false, note: 'TEST DATA nota' },
  { id: 51, versionNo: 1, parentVersionId: null, origin: 'PROFILE', jobOfferId: null, language: 'es', status: 'DRAFT', createdAt: '2026-11-20T10:00:00Z', approvedAt: null, hasPdf: false, note: null },
]

const baseJob: JobSummaryDto = {
  id: 1,
  title: 'TEST DATA Backend Engineer',
  companyName: 'TEST DATA — Example Corp',
  locationText: 'Remoto',
  country: null,
  region: 'LATAM',
  workModality: 'REMOTE',
  remoteScope: 'LATAM',
  boliviaEligibility: 'ELIGIBLE',
  engagementType: 'CONTRACTOR',
  contractType: 'FULL_TIME',
  seniority: 'MID',
  salaryMin: 3000,
  salaryMax: 4000,
  salaryCurrency: 'USD',
  salaryPeriod: 'MONTH',
  salaryIsEstimated: false,
  salaryText: null,
  salaryMinUsdMonth: 3000,
  publishedAt: '2026-11-24T10:00:00Z',
  publishedIsApprox: false,
  firstSeenAt: '2026-11-24T12:00:00Z',
  status: 'ACTIVE',
  skills: [{ id: 101, name: 'TEST DATA Java' }],
  sources: [{ code: 'remotive', displayName: 'Remotive', attributionText: 'Remotive', sourceUrl: 'https://example.test/remotive/1' }],
  saved: false,
  hidden: false,
  match: null,
}

export const jobs: JobSummaryDto[] = [
  baseJob,
  { ...baseJob, id: 2, title: 'TEST DATA Laravel Developer', boliviaEligibility: 'LIKELY', remoteScope: 'WORLDWIDE' },
  { ...baseJob, id: 3, title: 'TEST DATA Java Dev (US only)', boliviaEligibility: 'NOT_ELIGIBLE', remoteScope: 'COUNTRY_RESTRICTED' },
  { ...baseJob, id: 4, title: 'TEST DATA Platform Engineer', boliviaEligibility: 'UNKNOWN', remoteScope: 'UNKNOWN', sources: [{ code: 'himalayas', displayName: 'Himalayas', attributionText: 'Himalayas.app', sourceUrl: null }] },
]

export const jobDetail: JobDetailDto = {
  ...baseJob,
  descriptionHtml: '<p>TEST DATA <strong>descripción</strong> de la oferta.</p>',
  descriptionText: 'TEST DATA descripción de la oferta.',
  eligibilityEvidence: 'TEST DATA: «Open to candidates in LATAM».',
  remoteCountries: [],
  requiredLanguages: [{ code: 'en', level: 'B2', evidence: null }],
  applyUrl: 'https://example.test/apply/1',
  canonicalUrl: null,
  expiresAt: null,
  lastSeenAt: null,
  lastVerifiedAt: null,
  references: [
    {
      sourceCode: 'remotive',
      sourceName: 'Remotive',
      sourceUrl: 'https://example.test/remotive/1',
      applyUrl: 'https://example.test/apply/1',
      attributionText: 'Remotive',
      firstSeenAt: '2026-11-24T12:00:00Z',
      lastSeenAt: null,
      removedAt: null,
    },
  ],
}

export const sources: SourceDto[] = [
  {
    code: 'remotive',
    type: 'PUBLIC_API',
    displayName: 'Remotive',
    integrationCode: 'REMOTIVE',
    usable: true,
    reason: null,
    capabilities: { keyword: true, country: false, salary: false, postedSince: false, remoteOnly: true },
    attributionText: 'Remotive',
    termsUrl: null,
    termsNotes: null,
  },
  {
    code: 'adzuna',
    type: 'SEARCH_API',
    displayName: 'Adzuna',
    integrationCode: 'ADZUNA',
    usable: false,
    reason: 'Faltan credenciales',
    capabilities: { keyword: true, country: true, salary: true, postedSince: true, remoteOnly: false },
    attributionText: 'Adzuna',
    termsUrl: null,
    termsNotes: null,
  },
]

export const savedSearch: SavedSearchDto = {
  id: 9,
  name: 'TEST DATA Backend LATAM',
  profileId: null,
  keywords: 'backend',
  filters: emptyFilters(),
  sourceCodes: ['remotive'],
  scheduleType: 'DAILY',
  intervalMinutes: null,
  dailyTime: '08:00',
  timezone: 'America/La_Paz',
  enabled: true,
  maxResultsPerSource: 50,
  notifyOnNew: true,
  nextRunAt: null,
  lastRunAt: null,
  lastExecution: null,
  version: 0,
}

export const watchlist: WatchlistDto[] = [
  { id: 1, ats: 'GREENHOUSE', boardToken: 'examplecorp', companyName: 'TEST DATA — Example Corp', enabled: true, lastCheckedAt: null, lastStatus: null, lastJobCount: 12 },
]

export const notifications: NotificationDto[] = [
  { id: 1, type: 'NEW_JOBS', title: 'TEST DATA 3 ofertas nuevas', body: 'TEST DATA Backend LATAM', link: '/searches/9', createdAt: '2026-11-25T10:00:00Z', readAt: null },
  { id: 2, type: 'NEW_JOBS', title: 'TEST DATA ofertas leídas', body: null, link: null, createdAt: '2026-11-24T10:00:00Z', readAt: '2026-11-24T11:00:00Z' },
]

const usage = { requestsToday: 3, requestsThisMonth: 40, costThisMonthUsd: 0.12 }
export const integrations: IntegrationDto[] = [
  {
    code: 'DEEPSEEK',
    displayName: 'DeepSeek',
    category: 'AI',
    enabled: false,
    requiresCredentials: true,
    credentials: [{ name: 'apiKey', label: 'API key', required: true, configured: true, fromEnv: false, last4: 'ab12' }],
    config: {},
    dailyLimit: 100,
    monthlyLimit: null,
    monthlyBudgetUsd: 5,
    status: 'UNKNOWN',
    lastSuccessAt: null,
    lastErrorAt: null,
    lastError: null,
    usage,
    testable: true,
    version: 1,
  },
  {
    code: 'CLAUDE',
    displayName: 'Claude',
    category: 'AI',
    enabled: true,
    requiresCredentials: true,
    credentials: [{ name: 'apiKey', label: 'API key', required: true, configured: true, fromEnv: true, last4: null }],
    config: {},
    dailyLimit: null,
    monthlyLimit: null,
    monthlyBudgetUsd: null,
    status: 'OK',
    lastSuccessAt: '2026-11-25T10:00:00Z',
    lastErrorAt: null,
    lastError: null,
    usage,
    testable: true,
    version: 1,
  },
  {
    code: 'APIFY',
    displayName: 'Apify',
    category: 'SCRAPING',
    enabled: false,
    requiresCredentials: true,
    credentials: [{ name: 'token', label: 'Token', required: true, configured: false, fromEnv: false, last4: null }],
    config: {},
    dailyLimit: null,
    monthlyLimit: null,
    monthlyBudgetUsd: null,
    status: 'DISABLED',
    lastSuccessAt: null,
    lastErrorAt: null,
    lastError: null,
    usage,
    testable: false,
    version: 1,
  },
  {
    code: 'REMOTIVE',
    displayName: 'Remotive',
    category: 'JOB_SOURCE',
    enabled: true,
    requiresCredentials: false,
    credentials: [],
    config: {},
    dailyLimit: null,
    monthlyLimit: null,
    monthlyBudgetUsd: null,
    status: 'OK',
    lastSuccessAt: null,
    lastErrorAt: null,
    lastError: null,
    usage,
    testable: true,
    version: 1,
  },
]

export const adminSources: AdminSourceDto[] = [
  {
    code: 'remotive',
    type: 'PUBLIC_API',
    displayName: 'Remotive',
    enabled: true,
    termsUrl: 'https://example.test/terms',
    termsNotes: 'TEST DATA notas',
    attributionText: 'Remotive',
    attributionRequired: true,
    commercialUseAllowed: false,
    minIntervalMinutes: 60,
    dailyRequestCap: 24,
    lastFetchAt: null,
  },
]

export const fxRates: FxRateDto[] = [{ currency: 'EUR', rateToUsd: 1.08, asOf: '2026-11-01T00:00:00Z' }]

// ---- Phase 4 (TEST DATA) ----

export const analysis: AnalysisDto = {
  id: 1,
  status: 'SUCCEEDED',
  provider: 'DEEPSEEK',
  model: 'deepseek-flash',
  createdAt: '2026-11-26T10:00:00Z',
  costUsd: 0.0016,
  inputTokens: 1200,
  outputTokens: 300,
  error: null,
  output: {
    summary: 'TEST DATA: puesto backend remoto para LATAM.',
    seniority: 'MID',
    minYearsExperience: 3,
    mandatorySkills: [{ name: 'Java', minYears: 3 }],
    preferredSkills: [{ name: 'Kubernetes', minYears: null }],
    languages: [{ code: 'en', level: 'B2', mandatory: true }],
    salary: null,
    location: { scope: 'LATAM', countries: [], boliviaEligibility: 'ELIGIBLE', evidenceQuote: 'Open to candidates in LATAM' },
    engagementType: 'CONTRACTOR',
    contractType: 'FULL_TIME',
    injectionSuspected: true,
  },
}

export const requirements: RequirementDto[] = [
  { kind: 'SKILL', importance: 'MANDATORY', skillId: 101, text: 'TEST DATA Java', minYears: 3, languageCode: null, level: null, extractedBy: 'AI' },
  { kind: 'SKILL', importance: 'PREFERRED', skillId: null, text: 'TEST DATA Kubernetes', minYears: null, languageCode: null, level: null, extractedBy: 'DICTIONARY' },
]

export const match: MatchDto = {
  jobOfferId: 1,
  scoreTotal: 82,
  classification: 'HIGH_MATCH',
  recommendation: 'APPLY',
  method: 'DETERMINISTIC',
  scores: { skills: 90, experience: 80, location: 100, language: 70, seniority: 75, compensation: null, contract: 60 },
  reasons: [
    { name: 'skills', score: 90, reason: 'TEST DATA: 4 de 5 habilidades obligatorias con experiencia profesional.' },
    { name: 'location', score: 100, reason: 'TEST DATA: remoto LATAM, accesible desde Bolivia.' },
  ],
  strengths: ['TEST DATA: Java con 3,5 años profesionales'],
  gaps: ['TEST DATA: sin experiencia con Kubernetes'],
  updatedAt: '2026-11-26T10:00:00Z',
}

export const aiRoutes: AiRouteDto[] = [
  { promptKey: 'JOB_ANALYSIS', provider: 'DEEPSEEK', model: 'deepseek-flash', effort: null, thinking: 'OFF', fallbackProvider: null, fallbackModel: null, maxOutputTokens: 2000, implemented: true, version: 3 },
  { promptKey: 'MATCH_SEMANTIC', provider: 'CLAUDE', model: 'claude-test-model', effort: 'MEDIUM', thinking: null, fallbackProvider: null, fallbackModel: null, maxOutputTokens: null, implemented: true, version: 1 },
  { promptKey: 'COVER_LETTER', provider: 'CLAUDE', model: 'claude-test-model', effort: null, thinking: null, fallbackProvider: null, fallbackModel: null, maxOutputTokens: null, implemented: false, version: 0 },
]

export const promptVersions: PromptVersionDto[] = [
  { promptKey: 'JOB_ANALYSIS', version: 2, active: true, systemTemplate: 'TEST DATA system v2', userTemplate: 'TEST DATA user {{job}}', outputSchema: { type: 'object' }, temperature: 0.1, checksum: 'abcdef1234567890', notes: 'TEST DATA', createdAt: '2026-11-20T10:00:00Z' },
  { promptKey: 'JOB_ANALYSIS', version: 1, active: false, systemTemplate: 'TEST DATA system v1', userTemplate: 'TEST DATA user', outputSchema: { type: 'object' }, temperature: 0.2, checksum: '1234567890abcdef', notes: null, createdAt: '2026-11-01T10:00:00Z' },
]
