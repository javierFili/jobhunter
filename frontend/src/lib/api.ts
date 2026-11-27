// Fetch wrapper. The access token lives in memory only; the refresh token is an
// HttpOnly cookie (jh_refresh, path=/api/v1/auth) handled by the browser.
import { ApiError, parseProblem } from './problem'
import type { AuthResponse } from './types'

export const API_BASE = '/api/v1'

let accessToken: string | null = null
let refreshInFlight: Promise<AuthResponse | null> | null = null
let onSessionExpired: (() => void) | null = null
let onSessionRefreshed: ((auth: AuthResponse) => void) | null = null

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string | null): void {
  accessToken = token
}

/** Registers callbacks used by the auth layer (logout + redirect, user refresh). */
export function configureAuthHandlers(handlers: {
  onSessionExpired?: () => void
  onSessionRefreshed?: (auth: AuthResponse) => void
}): void {
  onSessionExpired = handlers.onSessionExpired ?? null
  onSessionRefreshed = handlers.onSessionRefreshed ?? null
}

function buildUrl(path: string, query?: Record<string, string | number | boolean | null | undefined>): string {
  const origin = typeof window !== 'undefined' ? window.location.origin : 'http://localhost'
  const url = new URL(API_BASE + path, origin)
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, String(v))
    }
  }
  return url.toString()
}

async function networkFetch(url: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(url, init)
  } catch {
    throw new ApiError({ status: 0 })
  }
}

/**
 * Calls POST /auth/refresh. Concurrent callers share one request so the
 * rotating refresh cookie is only used once.
 * - 200 → AuthResponse
 * - 401 (INVALID_REFRESH_TOKEN) / 403 (MISSING_CSRF_HEADER) / network error → null ("no session")
 * - 429 (RATE_LIMITED) → throws ApiError(429): the session may still be valid, callers keep the user in place.
 */
export function refreshSession(): Promise<AuthResponse | null> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        let res: Response
        try {
          res = await fetch(buildUrl('/auth/refresh'), {
            method: 'POST',
            credentials: 'include',
            headers: { 'X-Requested-With': 'XMLHttpRequest', Accept: 'application/json' },
          })
        } catch {
          accessToken = null
          return null
        }
        if (res.status === 429) throw new ApiError(await parseProblem(res))
        if (!res.ok) {
          accessToken = null
          return null
        }
        const auth = (await res.json()) as AuthResponse
        accessToken = auth.accessToken
        onSessionRefreshed?.(auth)
        return auth
      } finally {
        refreshInFlight = null
      }
    })()
  }
  return refreshInFlight
}

export function isRateLimited(error: unknown): boolean {
  return error instanceof ApiError && error.status === 429
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  query?: Record<string, string | number | boolean | null | undefined>
  /** Do not attach the bearer token nor attempt a refresh on 401 (login/register). */
  anonymous?: boolean
  /** Attach the token if present but never attempt a refresh on 401 (logout). */
  noRefresh?: boolean
  /** Send cookies and the CSRF header (auth endpoints). */
  withCredentials?: boolean
  signal?: AbortSignal
  /** 'blob' for binary downloads (PDF). */
  responseType?: 'json' | 'blob'
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, query, anonymous = false, noRefresh = false, withCredentials = false, signal, responseType = 'json' } = options
  const isForm = typeof FormData !== 'undefined' && body instanceof FormData

  const send = () => {
    const headers: Record<string, string> = { Accept: responseType === 'blob' ? '*/*' : 'application/json' }
    // For FormData the browser sets the multipart boundary itself.
    if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json'
    if (withCredentials) headers['X-Requested-With'] = 'XMLHttpRequest'
    if (!anonymous && accessToken) headers.Authorization = `Bearer ${accessToken}`
    return networkFetch(buildUrl(path, query), {
      method,
      headers,
      body: body === undefined ? undefined : isForm ? (body as FormData) : JSON.stringify(body),
      credentials: withCredentials ? 'include' : 'same-origin',
      signal,
    })
  }

  let response = await send()

  if (response.status === 401 && !anonymous && !noRefresh) {
    // A 429 from refresh propagates: the user stays on the page and can retry later.
    const refreshed = await refreshSession()
    if (refreshed) {
      response = await send()
    }
    if (!refreshed || response.status === 401) {
      accessToken = null
      onSessionExpired?.()
      throw new ApiError(await parseProblem(response))
    }
  }

  if (!response.ok) {
    throw new ApiError(await parseProblem(response))
  }
  if (response.status === 204) return undefined as T
  if (responseType === 'blob') return (await response.blob()) as T
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const api = {
  get: <T>(path: string, query?: RequestOptions['query'], signal?: AbortSignal) =>
    apiRequest<T>(path, { query, signal }),
  post: <T>(path: string, body?: unknown, opts?: Omit<RequestOptions, 'method' | 'body'>) =>
    apiRequest<T>(path, { ...opts, method: 'POST', body }),
  put: <T>(path: string, body?: unknown) => apiRequest<T>(path, { method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown) => apiRequest<T>(path, { method: 'PATCH', body }),
  delete: <T = void>(path: string) => apiRequest<T>(path, { method: 'DELETE' }),
  upload: <T>(path: string, form: FormData) => apiRequest<T>(path, { method: 'POST', body: form }),
  blob: (path: string, query?: RequestOptions['query'], signal?: AbortSignal) =>
    apiRequest<Blob>(path, { query, signal, responseType: 'blob' }),
}
