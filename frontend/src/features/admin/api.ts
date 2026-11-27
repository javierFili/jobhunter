import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type {
  AdminSourceDto,
  AiProvider,
  AiRouteDto,
  AiRouteUpdate,
  ApifyActorDto,
  ApifyActorRequest,
  FxRateDto,
  IntegrationCode,
  IntegrationDto,
  IntegrationUpdate,
  PromptKey,
  PromptVersionDto,
  UsageRow,
} from './types'

export const adminKeys = {
  integrations: ['admin', 'integrations'] as const,
  sources: ['admin', 'sources'] as const,
  actors: ['admin', 'apify', 'actors'] as const,
  fx: ['admin', 'fx-rates'] as const,
}

export function useIntegrations() {
  return useQuery({ queryKey: adminKeys.integrations, queryFn: ({ signal }) => api.get<IntegrationDto[]>('/admin/integrations', undefined, signal) })
}

function useSetIntegration() {
  const qc = useQueryClient()
  return (updated: IntegrationDto) => {
    qc.setQueryData<IntegrationDto[]>(adminKeys.integrations, (old) => old?.map((i) => (i.code === updated.code ? updated : i)))
    void qc.invalidateQueries({ queryKey: ['sources'] })
  }
}

export function useUpdateIntegration() {
  const set = useSetIntegration()
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ code, body }: { code: IntegrationCode; body: IntegrationUpdate }) => api.put<IntegrationDto>(`/admin/integrations/${code}`, body),
    onSuccess: set,
    onError: () => void qc.invalidateQueries({ queryKey: adminKeys.integrations }),
  })
}

export function useSaveSecret() {
  const set = useSetIntegration()
  return useMutation({
    mutationFn: ({ code, values }: { code: IntegrationCode; values: Record<string, string> }) =>
      api.put<IntegrationDto>(`/admin/integrations/${code}/secret`, values),
    onSuccess: set,
  })
}

export function useDeleteSecret() {
  const set = useSetIntegration()
  return useMutation({ mutationFn: (code: IntegrationCode) => api.delete<IntegrationDto>(`/admin/integrations/${code}/secret`), onSuccess: set })
}

export function useTestIntegration() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (code: IntegrationCode) => api.post<{ ok: boolean; message: string; details: unknown }>(`/admin/integrations/${code}/test`),
    onSettled: () => void qc.invalidateQueries({ queryKey: adminKeys.integrations }),
  })
}

// ---- sources ----
export function useAdminSources() {
  return useQuery({ queryKey: adminKeys.sources, queryFn: ({ signal }) => api.get<AdminSourceDto[]>('/admin/sources', undefined, signal) })
}

export function useUpdateAdminSource() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ code, body }: { code: string; body: { enabled?: boolean; minIntervalMinutes?: number | null; dailyRequestCap?: number | null } }) =>
      api.put<AdminSourceDto>(`/admin/sources/${code}`, body),
    onSuccess: (s) => {
      qc.setQueryData<AdminSourceDto[]>(adminKeys.sources, (old) => old?.map((x) => (x.code === s.code ? s : x)))
      void qc.invalidateQueries({ queryKey: ['sources'] })
    },
  })
}

// ---- apify actors ----
export function useApifyActors() {
  return useQuery({ queryKey: adminKeys.actors, queryFn: ({ signal }) => api.get<ApifyActorDto[]>('/admin/apify/actors', undefined, signal) })
}

export function useSaveApifyActor() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id?: number; body: ApifyActorRequest }) =>
      id ? api.put<ApifyActorDto>(`/admin/apify/actors/${id}`, body) : api.post<ApifyActorDto>('/admin/apify/actors', body),
    onSettled: () => void qc.invalidateQueries({ queryKey: adminKeys.actors }),
  })
}

export function useDeleteApifyActor() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api.delete(`/admin/apify/actors/${id}`),
    onSuccess: () => void qc.invalidateQueries({ queryKey: adminKeys.actors }),
  })
}

// ---- fx ----
export function useFxRates() {
  return useQuery({ queryKey: adminKeys.fx, queryFn: ({ signal }) => api.get<FxRateDto[]>('/admin/fx-rates', undefined, signal) })
}

export function useSaveFxRates() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { currency: string; rateToUsd: number }[]) => api.put<FxRateDto[]>('/admin/fx-rates', body),
    onSuccess: (list) => qc.setQueryData(adminKeys.fx, list),
  })
}

// ---- Phase 4: AI routes, prompts, usage ----
export const aiKeys = {
  routes: ['admin', 'ai', 'routes'] as const,
  models: (provider: AiProvider) => ['admin', 'ai', 'models', provider] as const,
  prompts: ['admin', 'prompts'] as const,
  promptVersions: (key: PromptKey) => ['admin', 'prompts', key, 'versions'] as const,
  usage: (from: string, to: string) => ['analytics', 'usage', from, to] as const,
}

export function useAiRoutes() {
  return useQuery({ queryKey: aiKeys.routes, queryFn: ({ signal }) => api.get<AiRouteDto[]>('/admin/ai/routes', undefined, signal) })
}

export function useUpdateAiRoute() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ key, body }: { key: PromptKey; body: AiRouteUpdate }) => api.put<AiRouteDto>(`/admin/ai/routes/${key}`, body),
    onSuccess: (route) => qc.setQueryData<AiRouteDto[]>(aiKeys.routes, (old) => old?.map((r) => (r.promptKey === route.promptKey ? route : r))),
    onError: () => void qc.invalidateQueries({ queryKey: aiKeys.routes }),
  })
}

export function useAiModels(provider: AiProvider) {
  return useQuery({
    queryKey: aiKeys.models(provider),
    queryFn: ({ signal }) => api.get<{ provider: AiProvider; models: string[]; source: 'API' | 'CONFIG' }>('/admin/ai/models', { provider }, signal),
    staleTime: 10 * 60_000,
  })
}

export function usePrompts() {
  return useQuery({ queryKey: aiKeys.prompts, queryFn: ({ signal }) => api.get<PromptVersionDto[]>('/admin/prompts', undefined, signal) })
}

export function usePromptVersions(key: PromptKey | null) {
  return useQuery({
    queryKey: aiKeys.promptVersions(key ?? 'JOB_ANALYSIS'),
    queryFn: ({ signal }) => api.get<PromptVersionDto[]>(`/admin/prompts/${key}/versions`, undefined, signal),
    enabled: key != null,
  })
}

export function useCreatePromptVersion(key: PromptKey) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { systemTemplate: string; userTemplate: string; outputSchema: object; temperature?: number | null; notes?: string | null }) =>
      api.post<PromptVersionDto>(`/admin/prompts/${key}/versions`, body),
    onSuccess: () => void qc.invalidateQueries({ queryKey: aiKeys.prompts }),
  })
}

export function useActivatePromptVersion(key: PromptKey) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (version: number) => api.post<PromptVersionDto>(`/admin/prompts/${key}/versions/${version}/activate`),
    onSuccess: () => void qc.invalidateQueries({ queryKey: aiKeys.prompts }),
  })
}

export function useUsage(from: string, to: string, enabled = true) {
  return useQuery({
    queryKey: aiKeys.usage(from, to),
    queryFn: ({ signal }) => api.get<UsageRow[]>('/analytics/usage', { from, to }, signal),
    enabled,
  })
}
