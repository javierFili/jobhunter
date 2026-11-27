import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { pollWhileRunning, type TaskStateDto } from '@/lib/task'
import type { BreakdownDimension, BreakdownRow, DailyUsageRow, FunnelStage, OverviewDto, SkillDemandRow, SkillRecommendations, TimelinePoint } from './types'

export interface Period {
  from: string
  to: string
}

const opts = { placeholderData: keepPreviousData, staleTime: 60_000 }

export function useOverview(p: Period) {
  return useQuery({ queryKey: ['analytics', 'overview', p], queryFn: ({ signal }) => api.get<OverviewDto>('/analytics/overview', { ...p }, signal), ...opts })
}

export function useBreakdown(dimension: BreakdownDimension, p: Period, limit = 15) {
  return useQuery({
    queryKey: ['analytics', 'breakdown', dimension, p, limit],
    queryFn: ({ signal }) => api.get<BreakdownRow[]>('/analytics/jobs/breakdown', { dimension, ...p, limit }, signal),
    ...opts,
  })
}

export function useTimeline(p: Period) {
  return useQuery({ queryKey: ['analytics', 'timeline', p], queryFn: ({ signal }) => api.get<TimelinePoint[]>('/analytics/jobs/timeline', { ...p }, signal), ...opts })
}

export function useFunnel(p: Period) {
  return useQuery({ queryKey: ['analytics', 'funnel', p], queryFn: ({ signal }) => api.get<FunnelStage[]>('/analytics/applications/funnel', { ...p }, signal), ...opts })
}

export function useSkillDemand(region: string, days: number) {
  return useQuery({
    queryKey: ['analytics', 'skills-demand', region, days],
    queryFn: ({ signal }) => api.get<SkillDemandRow[]>('/analytics/skills/demand', { region: region || undefined, days, limit: 20 }, signal),
    ...opts,
  })
}

export function useSkillRecommendations() {
  return useQuery({
    queryKey: ['analytics', 'skills-recommendations'],
    queryFn: ({ signal }) => api.get<SkillRecommendations>('/analytics/skills/recommendations', undefined, signal),
    refetchInterval: (q) => pollWhileRunning(q.state.data?.task.state),
  })
}

export function useGenerateSkillRecommendations() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ region, days }: { region: string; days: number }) =>
      api.post<TaskStateDto>('/analytics/skills/recommendations', undefined, { query: { region: region || undefined, days } }),
    onSuccess: (task) => {
      qc.setQueryData<SkillRecommendations>(['analytics', 'skills-recommendations'], (old) => ({ result: old?.result ?? null, task }))
      void qc.invalidateQueries({ queryKey: ['analytics', 'skills-recommendations'] })
    },
  })
}

export function useDailyUsage(p: Period, enabled: boolean) {
  return useQuery({
    queryKey: ['analytics', 'usage-daily', p],
    queryFn: ({ signal }) => api.get<DailyUsageRow[]>('/analytics/usage/daily', { ...p }, signal),
    enabled,
    ...opts,
  })
}
