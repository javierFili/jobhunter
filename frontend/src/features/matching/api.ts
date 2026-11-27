import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { pollWhileRunning, useOnTaskFinished, type TaskStateDto } from '@/lib/task'
import type { Page } from '@/lib/types'
import type { JobSummaryDto } from '@/features/jobs/types'
import type { Classification, JobAnalysisResponse, JobMatchResponse, MatchDto } from './types'

export const matchKeys = {
  analysis: (jobId: number) => ['jobs', 'detail', jobId, 'analysis'] as const,
  match: (jobId: number, profileId: number | null) => ['jobs', 'detail', jobId, 'match', profileId ?? 'default'] as const,
  recommended: (params: object) => ['jobs', 'recommended', params] as const,
  analyzeTop: ['matches', 'analyze-top'] as const,
}

const profileQuery = (profileId: number | null) => (profileId != null ? { profileId } : {})

// ---------- job analysis ----------
export function useJobAnalysis(jobId: number) {
  const qc = useQueryClient()
  const query = useQuery({
    queryKey: matchKeys.analysis(jobId),
    queryFn: ({ signal }) => api.get<JobAnalysisResponse>(`/jobs/${jobId}/analysis`, undefined, signal),
    refetchInterval: (q) => pollWhileRunning(q.state.data?.task.state),
  })
  useOnTaskFinished(query.data?.task.state, (state) => {
    // Eligibility may change after a successful analysis (only with verbatim evidence).
    if (state === 'SUCCEEDED') {
      void qc.invalidateQueries({ queryKey: ['jobs', 'detail', jobId], exact: true })
      void qc.invalidateQueries({ queryKey: ['jobs', 'detail', jobId, 'match'] })
    }
  })
  return query
}

export function useAnalyzeJob(jobId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (force: boolean) => api.post<TaskStateDto>(`/jobs/${jobId}/analyze`, undefined, { query: { force } }),
    onSuccess: (task) => {
      qc.setQueryData<JobAnalysisResponse>(matchKeys.analysis(jobId), (old) => (old ? { ...old, task } : { task, analysis: null, requirements: [] }))
      void qc.invalidateQueries({ queryKey: matchKeys.analysis(jobId) })
    },
  })
}

// ---------- match ----------
export function useJobMatch(jobId: number, profileId: number | null) {
  const qc = useQueryClient()
  const query = useQuery({
    queryKey: matchKeys.match(jobId, profileId),
    queryFn: ({ signal }) => api.get<JobMatchResponse>(`/jobs/${jobId}/match`, profileQuery(profileId), signal),
    refetchInterval: (q) => pollWhileRunning(q.state.data?.task.state),
  })
  useOnTaskFinished(query.data?.task.state, (state) => {
    if (state === 'SUCCEEDED') {
      // AI match may have run the job analysis too.
      void qc.invalidateQueries({ queryKey: matchKeys.analysis(jobId) })
      void qc.invalidateQueries({ queryKey: ['jobs', 'detail', jobId], exact: true })
      void qc.invalidateQueries({ queryKey: ['jobs', 'list'] })
      void qc.invalidateQueries({ queryKey: ['jobs', 'recommended'] })
    }
  })
  return query
}

export function useComputeMatch(jobId: number, profileId: number | null) {
  const qc = useQueryClient()
  const key = matchKeys.match(jobId, profileId)
  return useMutation({
    mutationFn: async (ai: boolean): Promise<{ ai: true; task: TaskStateDto } | { ai: false; match: MatchDto }> =>
      ai
        ? { ai: true, task: await api.post<TaskStateDto>(`/jobs/${jobId}/match`, undefined, { query: { ai: true, ...profileQuery(profileId) } }) }
        : { ai: false, match: await api.post<MatchDto>(`/jobs/${jobId}/match`, undefined, { query: { ai: false, ...profileQuery(profileId) } }) },
    onSuccess: (r) => {
      if (r.ai) {
        qc.setQueryData<JobMatchResponse>(key, (old) => ({ match: old?.match ?? null, task: r.task }))
        void qc.invalidateQueries({ queryKey: key })
      } else {
        qc.setQueryData<JobMatchResponse>(key, (old) => ({ task: old?.task ?? { state: 'NONE', code: null, message: null }, match: r.match }))
        void qc.invalidateQueries({ queryKey: ['jobs', 'list'] })
        void qc.invalidateQueries({ queryKey: ['jobs', 'recommended'] })
      }
    },
  })
}

// ---------- recommended ----------
export interface RecommendedParams {
  classification: Classification[]
  profileId: number | null
  page: number
  size: number
}

export function useRecommended(p: RecommendedParams) {
  return useQuery({
    queryKey: matchKeys.recommended(p),
    queryFn: ({ signal }) =>
      api.get<Page<JobSummaryDto>>(
        '/jobs/recommended',
        { classification: p.classification.join(',') || undefined, ...profileQuery(p.profileId), page: p.page, size: p.size },
        signal,
      ),
    placeholderData: keepPreviousData,
  })
}

export function useRecompute() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (profileId: number | null) => api.post<{ computed: number }>('/matches/recompute', undefined, { query: profileQuery(profileId) }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['jobs'] })
      void qc.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
}

export function useAnalyzeTopStatus() {
  const qc = useQueryClient()
  const query = useQuery({
    queryKey: matchKeys.analyzeTop,
    queryFn: ({ signal }) => api.get<TaskStateDto>('/matches/analyze-top', undefined, signal),
    refetchInterval: (q) => pollWhileRunning(q.state.data?.state),
  })
  useOnTaskFinished(query.data?.state, () => void qc.invalidateQueries({ queryKey: ['jobs'] }))
  return query
}

export function useAnalyzeTop() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ limit, profileId }: { limit: number; profileId: number | null }) =>
      api.post<TaskStateDto>('/matches/analyze-top', undefined, { query: { limit, ...profileQuery(profileId) } }),
    onSuccess: (task) => {
      qc.setQueryData(matchKeys.analyzeTop, task)
      void qc.invalidateQueries({ queryKey: matchKeys.analyzeTop })
    },
  })
}
