import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Page } from '@/lib/types'
import type { AppMethod, AppStatus, ApplicationDto, ApplicationEvent, ApplicationPatch, BoardDto, PreparePackage, Transitions } from './types'

export const appKeys = {
  all: ['applications'] as const,
  transitions: ['applications', 'transitions'] as const,
  board: ['applications', 'board'] as const,
  list: (p: object) => ['applications', 'list', p] as const,
  detail: (id: number) => ['applications', id] as const,
  events: (id: number) => ['applications', id, 'events'] as const,
  prepare: (id: number) => ['applications', id, 'prepare'] as const,
}

function useInvalidateApps() {
  const qc = useQueryClient()
  return () => {
    void qc.invalidateQueries({ queryKey: appKeys.all })
    void qc.invalidateQueries({ queryKey: ['dashboard'] })
    void qc.invalidateQueries({ queryKey: ['analytics'] })
  }
}

export function useTransitions() {
  return useQuery({ queryKey: appKeys.transitions, queryFn: ({ signal }) => api.get<Transitions>('/applications/transitions', undefined, signal), staleTime: Infinity })
}

export function useBoard() {
  return useQuery({ queryKey: appKeys.board, queryFn: ({ signal }) => api.get<BoardDto>('/applications/board', undefined, signal) })
}

export interface AppListParams {
  status: AppStatus[]
  q: string
  sort: 'updated' | 'applied' | 'followUp'
  page: number
  size: number
}

export function useApplications(p: AppListParams) {
  return useQuery({
    queryKey: appKeys.list(p),
    queryFn: ({ signal }) =>
      api.get<Page<ApplicationDto>>('/applications', { status: p.status.join(',') || undefined, q: p.q || undefined, sort: p.sort, page: p.page, size: p.size }, signal),
    placeholderData: keepPreviousData,
  })
}

export function useApplication(id: number) {
  return useQuery({ queryKey: appKeys.detail(id), queryFn: ({ signal }) => api.get<ApplicationDto>(`/applications/${id}`, undefined, signal) })
}

export function useEvents(id: number) {
  return useQuery({ queryKey: appKeys.events(id), queryFn: ({ signal }) => api.get<ApplicationEvent[]>(`/applications/${id}/events`, undefined, signal) })
}

export function useCreateApplication() {
  const invalidate = useInvalidateApps()
  return useMutation({
    mutationFn: (body: { jobOfferId: number } | { companyName: string; positionTitle: string; jobUrl?: string | null; sourceLabel?: string | null; method?: AppMethod | null; notes?: string | null }) =>
      api.post<ApplicationDto>('/applications', body),
    onSuccess: invalidate,
  })
}

export function usePatchApplication(id: number) {
  const qc = useQueryClient()
  const invalidate = useInvalidateApps()
  return useMutation({
    mutationFn: (body: ApplicationPatch) => api.patch<ApplicationDto>(`/applications/${id}`, body),
    onSuccess: (a) => {
      qc.setQueryData(appKeys.detail(id), a)
      invalidate()
    },
    onError: invalidate,
  })
}

export function useDeleteApplication() {
  const invalidate = useInvalidateApps()
  return useMutation({ mutationFn: (id: number) => api.delete(`/applications/${id}`), onSuccess: invalidate })
}

export function useTransition() {
  const invalidate = useInvalidateApps()
  return useMutation({
    mutationFn: ({ id, toStatus, note }: { id: number; toStatus: AppStatus; note?: string }) =>
      api.post<ApplicationDto>(`/applications/${id}/transition`, { toStatus, ...(note ? { note } : {}) }),
    onSettled: invalidate,
  })
}

export function useReorder() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { status: AppStatus; orderedIds: number[] }) => api.put<void>('/applications/kanban-order', body),
    onError: () => void qc.invalidateQueries({ queryKey: appKeys.board }),
  })
}

export function useAddEvent(id: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { type: 'NOTE' | 'MESSAGE_RECEIVED' | 'INTERVIEW_SCHEDULED' | 'FOLLOW_UP'; note: string; occurredAt?: string }) =>
      api.post<ApplicationEvent>(`/applications/${id}/events`, body),
    onSuccess: () => void qc.invalidateQueries({ queryKey: appKeys.events(id) }),
  })
}

export function usePrepare(id: number) {
  return useQuery({
    queryKey: appKeys.prepare(id),
    // POST, but it only assembles the package (no side effects besides the response).
    queryFn: () => api.post<PreparePackage>(`/applications/${id}/prepare`),
    staleTime: 0,
  })
}

export function useMarkSubmitted() {
  const invalidate = useInvalidateApps()
  return useMutation({
    mutationFn: ({ id, ...body }: { id: number; method: AppMethod; submittedAt?: string; note?: string }) =>
      api.post<ApplicationDto>(`/applications/${id}/mark-submitted`, body),
    onSuccess: invalidate,
  })
}
