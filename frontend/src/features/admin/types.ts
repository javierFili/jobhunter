export type IntegrationCode =
  | 'DEEPSEEK'
  | 'CLAUDE'
  | 'APIFY'
  | 'ADZUNA'
  | 'SERPAPI'
  | 'REMOTIVE'
  | 'HIMALAYAS'
  | 'JOBICY'
  | 'REMOTEOK'
  | 'GREENHOUSE'
  | 'LEVER'
  | 'ASHBY'
  | 'GETONBOARD'
  | 'WEWORKREMOTELY'
  | 'LARAJOBS'
  | 'FREELANCER'
export type IntegrationCategory = 'AI' | 'JOB_SOURCE' | 'SCRAPING'
export type IntegrationStatus = 'UNKNOWN' | 'OK' | 'ERROR' | 'DISABLED'

export interface CredentialField {
  name: string
  label: string
  required: boolean
  configured: boolean
  fromEnv: boolean
  last4: string | null
}

export interface IntegrationDto {
  code: IntegrationCode
  displayName: string
  category: IntegrationCategory
  enabled: boolean
  requiresCredentials: boolean
  credentials: CredentialField[]
  config: Record<string, unknown> | null
  dailyLimit: number | null
  monthlyLimit: number | null
  monthlyBudgetUsd: number | null
  status: IntegrationStatus
  lastSuccessAt: string | null
  lastErrorAt: string | null
  lastError: string | null
  usage: { requestsToday: number; requestsThisMonth: number; costThisMonthUsd: number }
  testable: boolean
  version: number
}

export interface IntegrationUpdate {
  enabled?: boolean
  config?: Record<string, unknown>
  dailyLimit?: number | null
  monthlyLimit?: number | null
  monthlyBudgetUsd?: number | null
  version: number
}

export interface AdminSourceDto {
  code: string
  type: string
  displayName: string
  enabled: boolean
  termsUrl: string | null
  termsNotes: string | null
  attributionText: string | null
  attributionRequired: boolean
  commercialUseAllowed: boolean | null
  minIntervalMinutes: number | null
  dailyRequestCap: number | null
  lastFetchAt: string | null
}

export interface ApifyActorDto {
  id: number
  actorId: string
  displayName: string
  platform: string
  enabled: boolean
  inputTemplate: unknown
  fieldMapping: unknown
  maxItems: number | null
  maxChargeUsd: number | null
  timeoutSecs: number | null
  pricingModel: string | null
  assessmentNotes: string | null
  assessedAt: string | null
  version: number
}

export type ApifyActorRequest = Omit<ApifyActorDto, 'id' | 'assessedAt' | 'version'> & { version?: number }

export interface FxRateDto {
  currency: string
  rateToUsd: number
  asOf: string | null
}

// ---- Phase 4 ----
export type PromptKey = 'JOB_ANALYSIS' | 'MATCH_SEMANTIC' | 'CV_PARSE' | 'CV_OPTIMIZE' | 'COVER_LETTER'
export type AiProvider = 'DEEPSEEK' | 'CLAUDE'
export type Effort = 'LOW' | 'MEDIUM' | 'HIGH' | 'XHIGH' | 'MAX'

export interface AiRouteDto {
  promptKey: PromptKey
  provider: AiProvider
  model: string
  effort: Effort | null
  thinking: 'ON' | 'OFF' | null
  fallbackProvider: AiProvider | null
  fallbackModel: string | null
  maxOutputTokens: number | null
  implemented: boolean
  version: number
}

export interface AiRouteUpdate {
  provider: AiProvider
  model: string
  effort?: Effort | null
  thinking?: 'ON' | 'OFF' | null
  fallbackProvider?: AiProvider | null
  fallbackModel?: string | null
  maxOutputTokens?: number | null
  version: number
}

export interface PromptVersionDto {
  promptKey: PromptKey
  version: number
  active: boolean
  systemTemplate: string
  userTemplate: string
  outputSchema: unknown
  temperature: number | null
  checksum: string
  notes: string | null
  createdAt: string
}

export interface UsageRow {
  code: string
  requests: number
  costUsd: number
  inputTokens: number
  outputTokens: number
  cachedTokens: number
}
