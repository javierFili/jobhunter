import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Page } from '@/lib/types'

export interface NotificationDto {
  id: number
  /** NEW_JOBS, FOLLOW_UP; unknown future types get a generic icon. */
  type: 'NEW_JOBS' | 'FOLLOW_UP' | (string & {})
  title: string
  body: string | null
  link: string | null
  createdAt: string
  readAt: string | null
}

export const UNREAD_POLL_MS = 60_000

export const notificationKeys = {
  unread: ['notifications', 'unread-count'] as const,
  list: ['notifications', 'list'] as const,
}

export function useUnreadCount() {
  return useQuery({
    queryKey: notificationKeys.unread,
    queryFn: ({ signal }) => api.get<{ count: number }>('/notifications/unread-count', undefined, signal),
    refetchInterval: UNREAD_POLL_MS,
    refetchIntervalInBackground: false,
  })
}

export function useNotifications(enabled: boolean) {
  return useQuery({
    queryKey: notificationKeys.list,
    queryFn: ({ signal }) => api.get<Page<NotificationDto>>('/notifications', { page: 0, size: 15 }, signal),
    enabled,
  })
}

function useInvalidate() {
  const qc = useQueryClient()
  return () => void qc.invalidateQueries({ queryKey: ['notifications'] })
}

export function useMarkRead() {
  const invalidate = useInvalidate()
  return useMutation({ mutationFn: (id: number) => api.post<void>(`/notifications/${id}/read`), onSuccess: invalidate })
}

export function useMarkAllRead() {
  const invalidate = useInvalidate()
  return useMutation({ mutationFn: () => api.post<void>('/notifications/read-all'), onSuccess: invalidate })
}
