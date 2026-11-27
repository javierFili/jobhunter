import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { API } from '@/test/handlers'
import { db } from '@/test/db'
import { renderApp } from '@/test/render'
import { server } from '@/test/server'

const overview = {
  jobs: { total: 500, active: 420, newInPeriod: 120, remote: 300, freelance: 25, eligibleOrLikely: 80 },
  applications: { total: 12, applied: 10, responses: 4, interviews: 2, offers: 1, rejections: 3, responseRate: 0.4 },
  matches: { HIGH_MATCH: 5, MEDIUM_MATCH: 10, LOW_MATCH: 30, NOT_ELIGIBLE: 8, NEEDS_REVIEW: 2 },
  usage: { aiCostUsd: 0.25, apifyCostUsd: 0, totalRequests: 340 },
}

function handlers(breakdowns: URLSearchParams[] = []) {
  server.use(
    http.get(`${API}/analytics/overview`, () => HttpResponse.json(overview)),
    http.get(`${API}/analytics/jobs/timeline`, () =>
      HttpResponse.json([
        { date: '2026-11-25', newJobs: 10, eligibleOrLikely: 4 },
        { date: '2026-11-26', newJobs: 12, eligibleOrLikely: 6 },
      ]),
    ),
    http.get(`${API}/analytics/jobs/breakdown`, ({ request }) => {
      const p = new URL(request.url).searchParams
      breakdowns.push(p)
      return HttpResponse.json(
        p.get('dimension') === 'eligibility'
          ? [{ key: 'ELIGIBLE', label: 'Accesible desde Bolivia', count: 50 }]
          : p.get('dimension') === 'country'
            ? [{ key: 'AR', label: 'TEST DATA Argentina', count: 9 }]
            : [{ key: 'java', label: 'TEST DATA Java', count: 40 }],
      )
    }),
    http.get(`${API}/analytics/applications/funnel`, () =>
      HttpResponse.json([
        { stage: 'CREATED', count: 12 },
        { stage: 'APPLIED', count: 10 },
        { stage: 'INTERVIEW', count: 2 },
      ]),
    ),
    http.get(`${API}/analytics/skills/demand`, () =>
      HttpResponse.json([
        { skillId: 101, name: 'TEST DATA Java', jobs: 40, share: 0.33, userEvidence: 'PROFESSIONAL' },
        { skillId: 200, name: 'TEST DATA Go', jobs: 12, share: 0.1, userEvidence: null },
      ]),
    ),
    http.get(`${API}/analytics/skills/recommendations`, () => HttpResponse.json({ task: { state: 'NONE', code: null, message: null }, result: null })),
    http.get(`${API}/analytics/usage/daily`, () =>
      HttpResponse.json([
        { date: '2026-11-25', code: 'DEEPSEEK', requests: 3, costUsd: 0.01 },
        { date: '2026-11-26', code: 'CLAUDE', requests: 1, costUsd: 0.02 },
      ]),
    ),
  )
  return breakdowns
}

describe('Analítica', () => {
  it('shows KPIs, charts as accessible tables and observed skill demand with the user level', async () => {
    const breakdowns = handlers()
    const { user } = renderApp('/analytics')
    const kpis = await screen.findByRole('region', { name: 'Indicadores del periodo' })
    expect(await within(kpis).findByText('120')).toBeInTheDocument()
    expect(within(kpis).getByText('40 %')).toBeInTheDocument()
    expect(await screen.findByRole('table', { name: 'Ofertas nuevas por día' })).toBeInTheDocument()
    expect(screen.getByRole('table', { name: 'Embudo de postulaciones' })).toHaveTextContent('Postuladas10')
    expect(screen.getByText('Datos observados')).toBeInTheDocument()
    const demand = screen.getByRole('table', { name: 'Demanda de tecnologías (datos observados)' })
    expect(within(demand).getByText('Experiencia profesional')).toBeInTheDocument()
    expect(within(demand).getByText('No la tienes')).toBeInTheDocument()
    // Regular users don't see costs
    expect(screen.queryByRole('heading', { name: 'Costo por día' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('combobox', { name: 'Dimensión' }))
    await user.click(await screen.findByRole('option', { name: 'País' }))
    expect(await screen.findByRole('table', { name: 'Ofertas por país' })).toHaveTextContent('TEST DATA Argentina')
    expect(breakdowns.some((p) => p.get('dimension') === 'country' && p.get('limit') === '15')).toBe(true)
  })

  it('keeps AI recommendations clearly separated and labelled', async () => {
    handlers()
    let started = false
    server.use(
      http.post(`${API}/analytics/skills/recommendations`, () => {
        started = true
        return HttpResponse.json({ state: 'RUNNING', code: null, message: null }, { status: 202 })
      }),
      http.get(`${API}/analytics/skills/recommendations`, () =>
        HttpResponse.json(
          started
            ? {
                task: { state: 'SUCCEEDED', code: null, message: null },
                result: {
                  generatedAt: '2026-11-27T10:00:00Z',
                  provider: 'DEEPSEEK',
                  model: 'deepseek-flash',
                  basedOnJobs: 120,
                  recommendations: [{ skill: 'TEST DATA Docker', priority: 'HIGH', reason: 'TEST DATA: aparece en 30% de las ofertas.' }],
                  disclaimer: 'TEST DATA: recomendación orientativa.',
                },
              }
            : { task: { state: 'NONE', code: null, message: null }, result: null },
        ),
      ),
    )
    const { user } = renderApp('/analytics')
    expect(await screen.findByRole('heading', { name: 'Recomendación generada por IA' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Generar recomendaciones' }))
    const list = await screen.findByRole('list', { name: 'Recomendaciones de la IA' })
    expect(within(list).getByText('Prioridad alta')).toBeInTheDocument()
    expect(screen.getByText('TEST DATA: recomendación orientativa.')).toBeInTheDocument()
  })

  it('shows daily cost per integration to admins', async () => {
    db.user = { ...db.user, role: 'ADMIN' }
    handlers()
    renderApp('/analytics')
    const table = await screen.findByRole('table', { name: 'Costo estimado por día e integración (USD)' })
    expect(within(table).getByText('CLAUDE')).toBeInTheDocument()
    expect(within(table).getByText('0.0200')).toBeInTheDocument()
  })
})

describe('Panel — postulaciones', () => {
  it('shows applications KPIs and a mini timeline', async () => {
    handlers()
    renderApp('/')
    const list = await screen.findByRole('list', { name: 'Resumen de postulaciones' })
    await waitFor(() => expect(list).toHaveTextContent('10Enviadas'))
    expect(list).toHaveTextContent('2Entrevistas')
    expect(list).toHaveTextContent('40 %Tasa de respuesta')
    expect(await screen.findByRole('table', { name: 'Ofertas nuevas por día' })).toBeInTheDocument()
  })
})
