import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { server } from '@/test/server'
import { API, problem } from '@/test/handlers'
import { api, configureAuthHandlers, getAccessToken, setAccessToken } from './api'
import { ApiError } from './problem'
import { handleFormError } from './zod'

describe('api client — Phase 2/3 additions', () => {
  it('sends FormData as multipart (no JSON content type) and returns JSON', async () => {
    let contentType: string | null = null
    let hasFile = false
    server.use(
      http.post(`${API}/resumes/uploads`, async ({ request }) => {
        contentType = request.headers.get('Content-Type')
        const form = await request.formData()
        // jsdom's File loses its name crossing into undici's FormData; only check the part exists.
        const part = form.get('file')
        hasFile = part !== null && typeof part !== 'string'
        return HttpResponse.json({ id: 1 }, { status: 201 })
      }),
    )
    const form = new FormData()
    form.append('file', new File(['%PDF-1.4 TEST DATA'], 'cv.pdf', { type: 'application/pdf' }))
    const res = await api.upload<{ id: number }>('/resumes/uploads', form)
    expect(res.id).toBe(1)
    expect(contentType).toMatch(/^multipart\/form-data; boundary=/)
    expect(hasFile).toBe(true)
  })

  it('downloads binary responses as Blob with the bearer token', async () => {
    setAccessToken('tok')
    let auth: string | null = null
    server.use(
      http.get(`${API}/resumes/1/versions/1/pdf`, ({ request }) => {
        auth = request.headers.get('Authorization')
        return new HttpResponse(new Uint8Array([37, 80, 68, 70]), { headers: { 'Content-Type': 'application/pdf' } })
      }),
    )
    const blob = await api.blob('/resumes/1/versions/1/pdf', { template: 'classic', inline: true })
    expect(blob.size).toBe(4)
    expect(auth).toBe('Bearer tok')
  })

  it('a 429 from refresh keeps the session (no logout) and surfaces RATE_LIMITED', async () => {
    setAccessToken('expired')
    const onSessionExpired = vi.fn()
    configureAuthHandlers({ onSessionExpired })
    server.use(
      http.get(`${API}/me`, () => problem(401, 'UNAUTHORIZED', 'expirado')),
      http.post(`${API}/auth/refresh`, () => problem(429, 'RATE_LIMITED', 'Demasiadas solicitudes')),
    )
    const err = (await api.get('/me').catch((e: unknown) => e)) as ApiError
    expect(err).toBeInstanceOf(ApiError)
    expect(err.status).toBe(429)
    expect(onSessionExpired).not.toHaveBeenCalled()
    expect(getAccessToken()).toBe('expired')
    configureAuthHandlers({})
  })

  it('a 403 MISSING_CSRF_HEADER from refresh is treated as "no session"', async () => {
    setAccessToken('expired')
    const onSessionExpired = vi.fn()
    configureAuthHandlers({ onSessionExpired })
    server.use(
      http.get(`${API}/me`, () => problem(401, 'UNAUTHORIZED', 'expirado')),
      http.post(`${API}/auth/refresh`, () => problem(403, 'MISSING_CSRF_HEADER', 'Falta cabecera')),
    )
    await expect(api.get('/me')).rejects.toBeInstanceOf(ApiError)
    expect(onSessionExpired).toHaveBeenCalledTimes(1)
    configureAuthHandlers({})
  })

  it('maps cross-field server errors (dateRangeValid / currentConsistent) to endDate', () => {
    const setError = vi.fn()
    const err = new ApiError({
      status: 400,
      code: 'VALIDATION_FAILED',
      detail: 'Datos inválidos',
      errors: [
        { field: 'dateRangeValid', message: 'Rango de fechas inválido' },
        { field: 'skillIds', message: 'Habilidad desconocida' },
      ],
    })
    handleFormError(err, setError, { skillIds: 'skills' })
    expect(setError).toHaveBeenCalledWith('endDate', { type: 'server', message: 'Rango de fechas inválido' })
    expect(setError).toHaveBeenCalledWith('skills', { type: 'server', message: 'Habilidad desconocida' })
  })
})
