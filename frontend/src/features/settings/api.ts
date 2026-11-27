import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { queryKeys } from '@/lib/queryKeys'
import type { ConsentDto, Theme, UserDto } from '@/lib/types'
import { useAuth } from '@/features/auth/auth-context'

export interface UpdateMeRequest {
  fullName?: string
  locale?: string
  timezone?: string
  theme?: Theme
}

export function useUpdateMe() {
  const { setUser } = useAuth()
  return useMutation({
    mutationFn: (body: UpdateMeRequest) => api.patch<UserDto>('/me', body),
    onSuccess: (user) => setUser(user),
  })
}

export function useChangePassword() {
  return useMutation({
    mutationFn: (body: { currentPassword: string; newPassword: string }) => api.put<void>('/me/password', body),
  })
}

export function useConsents() {
  return useQuery({ queryKey: queryKeys.consents, queryFn: ({ signal }) => api.get<ConsentDto[]>('/me/consents', undefined, signal) })
}

export function useUpdateConsents() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: Pick<ConsentDto, 'consentType' | 'provider' | 'granted'>[]) =>
      api.put<ConsentDto[]>('/me/consents', body),
    onSuccess: (list) => qc.setQueryData(queryKeys.consents, list),
  })
}
