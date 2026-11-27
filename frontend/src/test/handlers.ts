import { http, HttpResponse } from 'msw'
import type { ExperienceDto, ExperienceRequest } from '@/lib/types'
import { db } from './db'
import { catalogSkills, dashboardSummary } from './fixtures'
import * as f2 from './fixtures2'

export const API = '*/api/v1'
export const TEST_TOKEN = 'test-access-token'

export const authResponse = () => ({ accessToken: TEST_TOKEN, expiresIn: 900, user: db.user })

export const problem = (status: number, code: string, detail: string, errors?: { field: string; message: string }[]) =>
  HttpResponse.json(
    { type: 'about:blank', title: code, status, detail, code, ...(errors ? { errors } : {}) },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )

const skillName = (id: number) => catalogSkills.find((s) => s.id === id)?.name ?? `skill-${id}`

export const handlers = [
  // ---- auth ----
  http.post(`${API}/auth/refresh`, () => HttpResponse.json(authResponse())),
  http.post(`${API}/auth/logout`, () => new HttpResponse(null, { status: 204 })),
  http.post(`${API}/auth/login`, async ({ request }) => {
    const body = (await request.json()) as { email: string; password: string }
    if (body.password !== 'correct-password') return problem(401, 'INVALID_CREDENTIALS', 'Credenciales inválidas')
    return HttpResponse.json(authResponse())
  }),
  http.post(`${API}/auth/register`, async ({ request }) => {
    const body = (await request.json()) as { email: string; fullName: string }
    db.user = { ...db.user, email: body.email, fullName: body.fullName }
    return HttpResponse.json(authResponse(), { status: 201 })
  }),

  // ---- me ----
  http.get(`${API}/me`, () => HttpResponse.json(db.user)),
  http.patch(`${API}/me`, async ({ request }) => {
    db.user = { ...db.user, ...((await request.json()) as object) }
    return HttpResponse.json(db.user)
  }),
  http.get(`${API}/me/consents`, () => HttpResponse.json(db.consents)),
  http.put(`${API}/me/consents`, async ({ request }) => {
    const body = (await request.json()) as { consentType: string; provider: string; granted: boolean }[]
    db.consents = db.consents.map((c) => {
      const u = body.find((b) => b.consentType === c.consentType && b.provider === c.provider)
      return u ? { ...c, granted: u.granted, grantedAt: u.granted ? (c.grantedAt ?? '2026-11-26T10:00:00Z') : null } : c
    })
    return HttpResponse.json(db.consents)
  }),

  // ---- dashboard ----
  http.get(`${API}/dashboard/summary`, () => HttpResponse.json(dashboardSummary)),

  // ---- profiles ----
  http.get(`${API}/profiles`, () => HttpResponse.json(db.profiles)),

  // ---- experiences ----
  http.get(`${API}/experiences`, () => HttpResponse.json(db.experiences)),
  http.post(`${API}/experiences`, async ({ request }) => {
    const body = (await request.json()) as ExperienceRequest
    const { skillIds, ...rest } = body
    const created: ExperienceDto = {
      ...rest,
      id: db.nextId++,
      skills: skillIds.map((id) => ({ id, name: skillName(id) })),
      verified: false,
      source: 'MANUAL',
      version: 0,
    }
    db.experiences = [created, ...db.experiences]
    return HttpResponse.json(created, { status: 201 })
  }),

  // ---- projects ----
  http.get(`${API}/projects`, () => HttpResponse.json(db.projects)),

  // ---- skills ----
  http.get(`${API}/catalog/skills`, ({ request }) => {
    const q = (new URL(request.url).searchParams.get('q') ?? '').toLowerCase()
    return HttpResponse.json(catalogSkills.filter((s) => s.name.toLowerCase().includes(q)))
  }),
  http.get(`${API}/skills`, () => HttpResponse.json(db.skills)),

  // ---- Phase 2/3 defaults (individual tests override with server.use) ----
  http.get(`${API}/notifications/unread-count`, () => HttpResponse.json({ count: 0 })),
  http.get(`${API}/notifications`, () => HttpResponse.json({ content: [], page: 0, size: 15, totalElements: 0, totalPages: 0 })),
  http.get(`${API}/resume-templates`, () =>
    HttpResponse.json([
      { id: 'classic', name: 'Clásica', description: 'TEST DATA' },
      { id: 'modern', name: 'Moderna', description: 'TEST DATA' },
    ]),
  ),
  http.get(`${API}/sources`, () => HttpResponse.json(f2.sources)),
  http.get(`${API}/jobs/:id/analysis`, () => HttpResponse.json({ task: { state: 'NONE', code: null, message: null }, analysis: null, requirements: [] })),
  http.get(`${API}/jobs/:id/match`, () => HttpResponse.json({ task: { state: 'NONE', code: null, message: null }, match: null })),
  http.get(`${API}/analytics/usage`, () => HttpResponse.json([])),
  http.get(`${API}/analytics/overview`, () =>
    HttpResponse.json({
      jobs: { total: 0, active: 0, newInPeriod: 0, remote: 0, freelance: 0, eligibleOrLikely: 0 },
      applications: { total: 0, applied: 0, responses: 0, interviews: 0, offers: 0, rejections: 0, responseRate: null },
      matches: { HIGH_MATCH: 0, MEDIUM_MATCH: 0, LOW_MATCH: 0, NOT_ELIGIBLE: 0, NEEDS_REVIEW: 0 },
      usage: { aiCostUsd: 0, apifyCostUsd: 0, totalRequests: 0 },
    }),
  ),
  http.get(`${API}/analytics/jobs/timeline`, () => HttpResponse.json([])),
  http.get(`${API}/cover-letters`, () => HttpResponse.json({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 })),
  http.get(`${API}/matches/analyze-top`, () => HttpResponse.json({ state: 'NONE', code: null, message: null })),
  http.get(`${API}/resumes/uploads/:id/ai-parse`, () => HttpResponse.json({ state: 'NONE', code: null, message: null })),

  // ---- verify ----
  http.post(`${API}/profile/verify`, () => new HttpResponse(null, { status: 204 })),
]
