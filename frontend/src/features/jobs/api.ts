import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Page } from '@/lib/types'
import { toApiQuery } from './query'
import type { JobDetailDto, JobQuery, JobSummaryDto, SourceDto } from './types'

export const jobKeys = {
  all: ['jobs'] as const,
  list: (q: JobQuery) => ['jobs', 'list', toApiQuery(q)] as const,
  detail: (id: number) => ['jobs', 'detail', id] as const,
  sources: ['sources'] as const,
}

export function useJobs(q: JobQuery) {
  return useQuery({
    queryKey: jobKeys.list(q),
    queryFn: ({ signal }) => api.get<Page<JobSummaryDto>>('/jobs', toApiQuery(q), signal),
    placeholderData: keepPreviousData,
  })
}

export function useJob(id: number) {
  return useQuery({ queryKey: jobKeys.detail(id), queryFn: ({ signal }) => api.get<JobDetailDto>(`/jobs/${id}`, undefined, signal) })
}

export function useSources() {
  return useQuery({ queryKey: jobKeys.sources, queryFn: ({ signal }) => api.get<SourceDto[]>('/sources', undefined, signal), staleTime: 5 * 60_000 })
}

export function useJobState() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...body }: { id: number; saved?: boolean; hidden?: boolean }) =>
      api.put<{ saved: boolean; hidden: boolean }>(`/jobs/${id}/state`, body),
    onSuccess: (state, { id }) => {
      qc.setQueryData<JobDetailDto>(jobKeys.detail(id), (old) => (old ? { ...old, ...state } : old))
      // Update the job inside any cached result page.
      qc.setQueriesData<Page<JobSummaryDto>>({ queryKey: ['jobs', 'list'] }, (old) =>
        old ? { ...old, content: old.content.map((j) => (j.id === id ? { ...j, ...state } : j)) } : old,
      )
      void qc.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
}
