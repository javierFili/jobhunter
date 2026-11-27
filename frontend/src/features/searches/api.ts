import { useEffect, useRef } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Page } from '@/lib/types'
import type { JobSummaryDto } from '@/features/jobs/types'
import type { Ats, ExecutionDetailDto, ExecutionDto, SavedSearchDto, SavedSearchRequest, WatchlistDto } from './types'

export const searchKeys = {
  list: ['searches'] as const,
  detail: (id: number) => ['searches', id] as const,
  executions: (id: number, page: number) => ['searches', id, 'executions', page] as const,
  execution: (executionId: number) => ['searches', 'executions', executionId] as const,
  executionJobs: (executionId: number, onlyNew: boolean, page: number) => ['searches', 'executions', executionId, 'jobs', onlyNew, page] as const,
  watchlist: ['watchlist'] as const,
}

export const POLL_INTERVAL_MS = 3000

export function useSavedSearches() {
  return useQuery({ queryKey: searchKeys.list, queryFn: ({ signal }) => api.get<SavedSearchDto[]>('/searches', undefined, signal) })
}

export function useSavedSearch(id: number) {
  return useQuery({ queryKey: searchKeys.detail(id), queryFn: ({ signal }) => api.get<SavedSearchDto>(`/searches/${id}`, undefined, signal) })
}

function useInvalidateSearches() {
  const qc = useQueryClient()
  return () => {
    void qc.invalidateQueries({ queryKey: searchKeys.list })
    void qc.invalidateQueries({ queryKey: ['dashboard'] })
  }
}

export function useCreateSearch() {
  const invalidate = useInvalidateSearches()
  return useMutation({ mutationFn: (body: SavedSearchRequest) => api.post<SavedSearchDto>('/searches', body), onSuccess: invalidate })
}

export function useUpdateSearch() {
  const qc = useQueryClient()
  const invalidate = useInvalidateSearches()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: SavedSearchRequest }) => api.put<SavedSearchDto>(`/searches/${id}`, body),
    onSuccess: (s) => {
      qc.setQueryData(searchKeys.detail(s.id), s)
      invalidate()
    },
    onError: invalidate,
  })
}

export function useDeleteSearch() {
  const invalidate = useInvalidateSearches()
  return useMutation({ mutationFn: (id: number) => api.delete(`/searches/${id}`), onSuccess: invalidate })
}

export function useRunSearch() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api.post<{ executionId: number; status: 'RUNNING' }>(`/searches/${id}/run`),
    onSuccess: (_r, id) => {
      void qc.invalidateQueries({ queryKey: ['searches', id, 'executions'] })
      void qc.invalidateQueries({ queryKey: searchKeys.list })
    },
  })
}

export function useExecutions(searchId: number, page: number) {
  return useQuery({
    queryKey: searchKeys.executions(searchId, page),
    queryFn: ({ signal }) => api.get<Page<ExecutionDto>>(`/searches/${searchId}/executions`, { page, size: 10 }, signal),
    placeholderData: keepPreviousData,
  })
}

/** Polls every 3 s while the execution is RUNNING; refreshes related lists once it finishes. */
export function useExecution(executionId: number | null) {
  const qc = useQueryClient()
  const query = useQuery({
    queryKey: searchKeys.execution(executionId ?? -1),
    queryFn: ({ signal }) => api.get<ExecutionDetailDto>(`/searches/executions/${executionId}`, undefined, signal),
    enabled: executionId != null,
    refetchInterval: (q) => (q.state.data?.status === 'RUNNING' ? POLL_INTERVAL_MS : false),
  })
  const status = query.data?.status
  const sawRunning = useRef(false)
  useEffect(() => {
    if (status === 'RUNNING') {
      sawRunning.current = true
    } else if (status && sawRunning.current) {
      sawRunning.current = false
      // Never invalidate the execution query itself here (that would cancel/restart it in a loop).
      const self = searchKeys.execution(executionId ?? -1)
      void qc.invalidateQueries({
        predicate: (q) =>
          (q.queryKey[0] === 'searches' && JSON.stringify(q.queryKey) !== JSON.stringify(self)) ||
          q.queryKey[0] === 'jobs' ||
          q.queryKey[0] === 'dashboard',
      })
    }
  }, [status, executionId, qc])
  return query
}

export function useExecutionJobs(executionId: number | null, onlyNew: boolean, page: number, enabled: boolean) {
  return useQuery({
    queryKey: searchKeys.executionJobs(executionId ?? -1, onlyNew, page),
    queryFn: ({ signal }) =>
      api.get<Page<JobSummaryDto>>(`/searches/executions/${executionId}/jobs`, { onlyNew, page, size: 10 }, signal),
    enabled: executionId != null && enabled,
    placeholderData: keepPreviousData,
  })
}

// ---- watchlist ----
export function useWatchlist() {
  return useQuery({ queryKey: searchKeys.watchlist, queryFn: ({ signal }) => api.get<WatchlistDto[]>('/watchlist', undefined, signal) })
}

export function useAddWatch() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { ats: Ats; boardToken: string; companyName?: string | null }) => api.post<WatchlistDto>('/watchlist', body),
    onSuccess: () => void qc.invalidateQueries({ queryKey: searchKeys.watchlist }),
  })
}

export function useUpdateWatch() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...body }: { id: number; enabled?: boolean; companyName?: string }) => api.put<WatchlistDto>(`/watchlist/${id}`, body),
    onSuccess: () => void qc.invalidateQueries({ queryKey: searchKeys.watchlist }),
  })
}

export function useDeleteWatch() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api.delete(`/watchlist/${id}`),
    onSuccess: () => void qc.invalidateQueries({ queryKey: searchKeys.watchlist }),
  })
}

export function useValidateWatch() {
  return useMutation({
    mutationFn: (body: { ats: Ats; boardToken: string }) => api.post<{ ok: boolean; jobCount: number; message: string }>('/watchlist/validate', body),
  })
}
