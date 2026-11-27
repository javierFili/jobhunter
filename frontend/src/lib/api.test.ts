import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { server } from '@/test/server'
import { API, problem } from '@/test/handlers'
import { testUser } from '@/test/fixtures'
import { api, configureAuthHandlers, getAccessToken, setAccessToken } from './api'
import { ApiError } from './problem'

describe('api client', () => {
  it('on 401 refreshes once (cookie + X-Requested-With) and retries with the new token', async () => {
    setAccessToken('expired-token')
    const seenTokens: (string | null)[] = []
    const refreshCalls: { header: string | null }[] = []
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
    server.use(
      http.get(`${API}/me`, ({ request }) => {
        const auth = request.headers.get('Authorization')
        seenTokens.push(auth)
        if (auth !== 'Bearer fresh-token') return problem(401, 'UNAUTHORIZED', 'Token expirado')
        return HttpResponse.json(testUser)
      }),
      http.post(`${API}/auth/refresh`, ({ request }) => {
        refreshCalls.push({ header: request.headers.get('X-Requested-With') })
        return HttpResponse.json({ accessToken: 'fresh-token', expiresIn: 900, user: testUser })
      }),
    )

    const me = await api.get<typeof testUser>('/me')

    expect(me.email).toBe(testUser.email)
    expect(seenTokens).toEqual(['Bearer expired-token', 'Bearer fresh-token'])
    expect(refreshCalls).toHaveLength(1)
    expect(refreshCalls[0].header).toBe('XMLHttpRequest')
    const refreshInit = fetchSpy.mock.calls.find(([url]) => String(url).endsWith('/api/v1/auth/refresh'))?.[1]
    expect(refreshInit?.credentials).toBe('include')
    expect(getAccessToken()).toBe('fresh-token')
  })

  it('shares a single refresh between concurrent 401 responses', async () => {
    setAccessToken('expired-token')
    let refreshes = 0
    server.use(
      http.get(`${API}/me`, ({ request }) =>
        request.headers.get('Authorization') === 'Bearer fresh-token'
          ? HttpResponse.json(testUser)
          : problem(401, 'UNAUTHORIZED', 'Token expirado'),
      ),
      http.post(`${API}/auth/refresh`, async () => {
        refreshes++
        await new Promise((r) => setTimeout(r, 20))
        return HttpResponse.json({ accessToken: 'fresh-token', expiresIn: 900, user: testUser })
      }),
    )
    await Promise.all([api.get('/me'), api.get('/me'), api.get('/me')])
    expect(refreshes).toBe(1)
  })

  it('when refresh fails: clears the token, notifies session expiry and throws', async () => {
    setAccessToken('expired-token')
    const onSessionExpired = vi.fn()
    configureAuthHandlers({ onSessionExpired })
    server.use(
      http.get(`${API}/me`, () => problem(401, 'UNAUTHORIZED', 'Token expirado')),
      http.post(`${API}/auth/refresh`, () => problem(401, 'UNAUTHORIZED', 'Refresh inválido')),
    )
    await expect(api.get('/me')).rejects.toBeInstanceOf(ApiError)
    expect(onSessionExpired).toHaveBeenCalledTimes(1)
    expect(getAccessToken()).toBeNull()
    configureAuthHandlers({})
  })

  it('parses RFC 9457 problem details including field errors', async () => {
    server.use(
      http.post(`${API}/experiences`, () =>
        problem(400, 'VALIDATION_FAILED', 'Datos inválidos', [{ field: 'companyName', message: 'Requerido' }]),
      ),
    )
    const err = (await api.post('/experiences', {}).catch((e: unknown) => e)) as ApiError
    expect(err).toBeInstanceOf(ApiError)
    expect(err.status).toBe(400)
    expect(err.code).toBe('VALIDATION_FAILED')
    expect(err.message).toBe('Datos inválidos')
    expect(err.fieldErrors).toEqual([{ field: 'companyName', message: 'Requerido' }])
  })

  it('maps 409 CONFLICT without detail to a friendly Spanish message', async () => {
    server.use(http.put(`${API}/experiences/1`, () => HttpResponse.json({ status: 409, code: 'CONFLICT' }, { status: 409 })))
    const err = (await api.put('/experiences/1', {}).catch((e: unknown) => e)) as ApiError
    expect(err.code).toBe('CONFLICT')
    expect(err.message).toMatch(/Otro cambio se guardó antes/)
  })
})
