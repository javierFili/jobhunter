import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { pollWhileRunning, useOnTaskFinished, type TaskStateDto } from '@/lib/task'
import type { Page } from '@/lib/types'
import type { CoverLetterDto, CoverLetterSummaryDto, LetterFormat, LetterLanguage, LetterTone } from './types'

export const letterKeys = {
  list: (p: object) => ['cover-letters', 'list', p] as const,
  detail: (id: number) => ['cover-letters', id] as const,
  versions: (id: number) => ['cover-letters', id, 'versions'] as const,
  pdf: (id: number) => ['cover-letters', id, 'pdf'] as const,
  task: (jobId: number) => ['jobs', 'detail', jobId, 'cover-letter-task'] as const,
}

export function useCoverLetters(
  p: { jobId?: number | null; format?: LetterFormat | null; status?: 'DRAFT' | 'APPROVED' | null; page?: number; size?: number },
  enabled = true,
) {
  return useQuery({
    queryKey: letterKeys.list(p),
    queryFn: ({ signal }) =>
      api.get<Page<CoverLetterSummaryDto>>(
        '/cover-letters',
        { jobId: p.jobId ?? undefined, format: p.format ?? undefined, status: p.status ?? undefined, page: p.page ?? 0, size: p.size ?? 50 },
        signal,
      ),
    placeholderData: keepPreviousData,
    enabled,
  })
}

export function useCoverLetter(id: number) {
  return useQuery({ queryKey: letterKeys.detail(id), queryFn: ({ signal }) => api.get<CoverLetterDto>(`/cover-letters/${id}`, undefined, signal) })
}

export function useLetterVersions(id: number) {
  return useQuery({
    queryKey: letterKeys.versions(id),
    queryFn: ({ signal }) => api.get<CoverLetterSummaryDto[]>(`/cover-letters/${id}/versions`, undefined, signal),
  })
}

export function useLetterPdf(id: number, enabled: boolean) {
  return useQuery({
    queryKey: letterKeys.pdf(id),
    queryFn: ({ signal }) => api.blob(`/cover-letters/${id}/pdf`, undefined, signal),
    enabled,
    staleTime: 5 * 60_000,
  })
}

function useInvalidateLetters() {
  const qc = useQueryClient()
  return () => void qc.invalidateQueries({ queryKey: ['cover-letters'] })
}

export function useSaveLetter(id: number) {
  const invalidate = useInvalidateLetters()
  return useMutation({
    mutationFn: (body: { subject?: string | null; body: string }) => api.put<CoverLetterDto>(`/cover-letters/${id}`, body),
    onSuccess: invalidate,
  })
}

export function useApproveLetter(id: number) {
  const qc = useQueryClient()
  const invalidate = useInvalidateLetters()
  return useMutation({
    mutationFn: () => api.post<CoverLetterDto>(`/cover-letters/${id}/approve`),
    onSuccess: (l) => {
      qc.setQueryData(letterKeys.detail(id), l)
      invalidate()
    },
  })
}

export function useDeleteLetter() {
  const invalidate = useInvalidateLetters()
  return useMutation({ mutationFn: (id: number) => api.delete(`/cover-letters/${id}`), onSuccess: invalidate })
}

export function useLetterTask(jobId: number, enabled: boolean) {
  const qc = useQueryClient()
  const query = useQuery({
    queryKey: letterKeys.task(jobId),
    queryFn: ({ signal }) => api.get<TaskStateDto>(`/jobs/${jobId}/cover-letters/task`, undefined, signal),
    enabled,
    refetchInterval: (q) => pollWhileRunning(q.state.data?.state),
  })
  useOnTaskFinished(query.data?.state, (state) => {
    if (state === 'SUCCEEDED') void qc.invalidateQueries({ queryKey: ['cover-letters'] })
  })
  return query
}

export function useGenerateLetter(jobId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { format: LetterFormat; language: LetterLanguage; tone?: LetterTone | null; profileId?: number | null }) =>
      api.post<TaskStateDto>(`/jobs/${jobId}/cover-letters`, body),
    onSuccess: (task) => {
      qc.setQueryData(letterKeys.task(jobId), task)
      void qc.invalidateQueries({ queryKey: letterKeys.task(jobId) })
    },
  })
}
