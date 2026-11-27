import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { api, configureAuthHandlers, isRateLimited, refreshSession, setAccessToken } from '@/lib/api'
import { useTheme } from '@/lib/theme-context'
import type { AuthResponse, UserDto } from '@/lib/types'
import { AuthContext, type AuthStatus, type LoginInput, type RegisterInput } from './auth-context'

export const RESTORE_RETRY_MS = 5000

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const { setTheme } = useTheme()
  const [user, setUserState] = useState<UserDto | null>(null)
  const [status, setStatus] = useState<AuthStatus>('loading')

  const applyUser = useCallback(
    (next: UserDto | null) => {
      setUserState(next)
      setStatus(next ? 'authenticated' : 'anonymous')
      if (next) setTheme(next.theme)
    },
    [setTheme],
  )

  useEffect(() => {
    configureAuthHandlers({
      onSessionExpired: () => {
        setAccessToken(null)
        queryClient.clear()
        setUserState((prev) => {
          if (prev) toast.error('Tu sesión expiró. Inicia sesión nuevamente.')
          return null
        })
        setStatus('anonymous')
      },
      onSessionRefreshed: (auth) => setUserState(auth.user),
    })
    let cancelled = false
    let retryTimer: ReturnType<typeof setTimeout> | undefined
    // Restore the session from the refresh cookie on app start.
    // On 429 (RATE_LIMITED) keep waiting and retry later instead of logging out.
    const restore = (attempt: number) => {
      refreshSession().then(
        (auth) => {
          if (!cancelled) applyUser(auth?.user ?? null)
        },
        (err: unknown) => {
          if (cancelled) return
          if (isRateLimited(err) && attempt < 5) retryTimer = setTimeout(() => restore(attempt + 1), RESTORE_RETRY_MS * (attempt + 1))
          else applyUser(null)
        },
      )
    }
    restore(0)
    return () => {
      cancelled = true
      clearTimeout(retryTimer)
      configureAuthHandlers({})
    }
  }, [queryClient, applyUser])

  const handleAuth = useCallback(
    (auth: AuthResponse) => {
      setAccessToken(auth.accessToken)
      queryClient.clear()
      applyUser(auth.user)
      return auth.user
    },
    [queryClient, applyUser],
  )

  const login = useCallback(
    async (input: LoginInput) =>
      handleAuth(await api.post<AuthResponse>('/auth/login', input, { anonymous: true, withCredentials: true })),
    [handleAuth],
  )

  const register = useCallback(
    async (input: RegisterInput) =>
      handleAuth(await api.post<AuthResponse>('/auth/register', input, { anonymous: true, withCredentials: true })),
    [handleAuth],
  )

  const logout = useCallback(async () => {
    try {
      await api.post<void>('/auth/logout', undefined, { noRefresh: true, withCredentials: true })
    } catch {
      // Logging out locally is enough if the server call fails.
    }
    setAccessToken(null)
    queryClient.clear()
    applyUser(null)
  }, [queryClient, applyUser])

  const value = useMemo(
    () => ({ status, user, login, register, logout, setUser: (u: UserDto) => setUserState(u) }),
    [status, user, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
