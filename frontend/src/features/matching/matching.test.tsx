import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { API, problem } from '@/test/handlers'
import { analysis, jobDetail, jobs, match, requirements } from '@/test/fixtures2'
import { renderApp } from '@/test/render'
import { server } from '@/test/server'

const NONE = { state: 'NONE', code: null, message: null }
const RUNNING = { state: 'RUNNING', code: null, message: null }
const SUCCEEDED = { state: 'SUCCEEDED', code: null, message: null }

function detail(counter = { n: 0 }) {
  server.use(
    http.get(`${API}/jobs/1`, () => {
      counter.n++
      return HttpResponse.json(jobDetail)
    }),
  )
  return counter
}

describe('Análisis con IA', () => {
  it('runs the analysis, polls until done, shows requirements, cost and the injection warning, and refetches the job', async () => {
    const jobFetches = detail()
    let polls = 0
    let posted: string | null = null
    server.use(
      http.post(`${API}/jobs/1/analyze`, ({ request }) => {
        posted = new URL(request.url).search
        return HttpResponse.json(RUNNING, { status: 202 })
      }),
      http.get(`${API}/jobs/1/analysis`, () => {
        if (posted === null) return HttpResponse.json({ task: NONE, analysis: null, requirements: [] })
        polls++
        return polls === 1
          ? HttpResponse.json({ task: RUNNING, analysis: null, requirements: [] })
          : HttpResponse.json({ task: SUCCEEDED, analysis, requirements })
      }),
    )
    const { user } = renderApp('/jobs/1')
    await user.click(await screen.findByRole('button', { name: 'Analizar oferta' }))
    expect(posted).toBe('?force=false')
    expect(await screen.findByText('Analizado con DeepSeek deepseek-flash · US$0.0016', {}, { timeout: 10_000 })).toBeInTheDocument()
    expect(screen.getByText('La oferta contiene texto que intentó dar instrucciones a la IA; revisa con cuidado.')).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Obligatorias' })).getByText('TEST DATA Java')).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Deseables' })).getByText('TEST DATA Kubernetes')).toBeInTheDocument()
    expect(screen.getByText(/Inglés B2 · obligatorio/)).toBeInTheDocument()
    await waitFor(() => expect(jobFetches.n).toBeGreaterThanOrEqual(2))
    // Re-analysis forces a new run
    await user.click(screen.getByRole('button', { name: 'Reanalizar' }))
    await waitFor(() => expect(posted).toBe('?force=true'))
  })

  it('explains CONSENT_REQUIRED with a link to the consents settings', async () => {
    detail()
    server.use(
      http.get(`${API}/jobs/1/analysis`, () =>
        HttpResponse.json({ task: { state: 'FAILED', code: 'CONSENT_REQUIRED', message: 'consent' }, analysis: null, requirements: [] }),
      ),
    )
    renderApp('/jobs/1')
    expect(await screen.findByText('Necesitas dar tu consentimiento para que el proveedor de IA procese estos datos.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Consentimientos de IA/ })).toHaveAttribute('href', '/settings#consentimientos')
  })

  it('explains 409 ALREADY_RUNNING from the top "Analizar" button', async () => {
    detail()
    server.use(http.post(`${API}/jobs/1/analyze`, () => problem(409, 'ALREADY_RUNNING', 'running')))
    const { user } = renderApp('/jobs/1')
    await user.click(await screen.findByRole('button', { name: 'Analizar' }))
    expect(await screen.findByText('Ya hay un proceso en curso. Espera a que termine.')).toBeInTheDocument()
  })
})

describe('Compatibilidad', () => {
  it('computes the deterministic match and shows score, bars, strengths, gaps and the disclaimer', async () => {
    detail()
    let query = ''
    server.use(
      http.post(`${API}/jobs/1/match`, ({ request }) => {
        query = new URL(request.url).search
        return HttpResponse.json(match)
      }),
    )
    const { user } = renderApp('/jobs/1')
    expect(await screen.findByText('La puntuación estima la compatibilidad con tu perfil verificado; no es una probabilidad de contratación.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Calcular' }))
    expect(await screen.findByLabelText('Puntuación total 82 de 100')).toBeInTheDocument()
    expect(query).toBe('?ai=false')
    expect(screen.getByText('Alta coincidencia')).toBeInTheDocument()
    expect(screen.getByText('Postular')).toBeInTheDocument()
    expect(screen.getByText('Determinista')).toBeInTheDocument()
    expect(screen.getByRole('meter', { name: 'Habilidades' })).toHaveAttribute('aria-valuenow', '90')
    expect(screen.getByRole('meter', { name: 'Compensación' })).toHaveAttribute('aria-valuetext', 'Sin datos')
    expect(screen.getByText('TEST DATA: 4 de 5 habilidades obligatorias con experiencia profesional.')).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Fortalezas' })).getByText(/Java con 3,5 años/)).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Brechas' })).getByText(/Kubernetes/)).toBeInTheDocument()
  })

  it('runs the AI-assisted evaluation with polling', async () => {
    detail()
    let started = false
    let polls = 0
    const aiMatch = { ...match, method: 'AI_ASSISTED', reasons: [...match.reasons, { name: 'ai', score: 85, reason: 'TEST DATA: la IA considera relevante tu experiencia backend.' }], gaps: ['IA: TEST DATA falta experiencia con colas de mensajes'] }
    server.use(
      http.post(`${API}/jobs/1/match`, ({ request }) => {
        expect(new URL(request.url).searchParams.get('ai')).toBe('true')
        started = true
        return HttpResponse.json(RUNNING, { status: 202 })
      }),
      http.get(`${API}/jobs/1/match`, () => {
        if (!started) return HttpResponse.json({ task: NONE, match: null })
        polls++
        return HttpResponse.json(polls === 1 ? { task: RUNNING, match: null } : { task: SUCCEEDED, match: aiMatch })
      }),
    )
    const { user } = renderApp('/jobs/1')
    await user.click(await screen.findByRole('button', { name: 'Evaluar con IA' }))
    expect(await screen.findByText('Asistido por IA', {}, { timeout: 10_000 })).toBeInTheDocument()
    expect(screen.getByText('TEST DATA: la IA considera relevante tu experiencia backend.')).toBeInTheDocument()
    expect(screen.getByText('IA: TEST DATA falta experiencia con colas de mensajes')).toBeInTheDocument()
  })

  it('shows a score chip on job cards when the match exists', async () => {
    server.use(
      http.get(`${API}/jobs`, () =>
        HttpResponse.json({ content: [{ ...jobs[0], match: { scoreTotal: 82, classification: 'HIGH_MATCH' } }], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
      ),
    )
    renderApp('/jobs')
    const card = await screen.findByRole('article', { name: 'TEST DATA Backend Engineer' })
    expect(within(card).getByText('82')).toBeInTheDocument()
    expect(card).toHaveTextContent('Compatibilidad: 82 de 100, Alta coincidencia')
  })
})

describe('Empleos recomendados', () => {
  it('filters by classification, recalculates and evaluates the top jobs with AI', async () => {
    const requests: URLSearchParams[] = []
    let topQuery = ''
    let topStarted = false
    server.use(
      http.get(`${API}/jobs/recommended`, ({ request }) => {
        requests.push(new URL(request.url).searchParams)
        return HttpResponse.json({ content: [{ ...jobs[0], match: { scoreTotal: 82, classification: 'HIGH_MATCH' } }], page: 0, size: 20, totalElements: 1, totalPages: 1 })
      }),
      http.post(`${API}/matches/recompute`, () => HttpResponse.json({ computed: 42 })),
      http.post(`${API}/matches/analyze-top`, ({ request }) => {
        topQuery = new URL(request.url).search
        topStarted = true
        return HttpResponse.json(RUNNING, { status: 202 })
      }),
      http.get(`${API}/matches/analyze-top`, () =>
        HttpResponse.json(topStarted ? { state: 'FAILED', code: 'BUDGET_EXCEEDED', message: 'budget' } : NONE),
      ),
    )
    const { user } = renderApp('/recommendations')
    expect(await screen.findByRole('article', { name: 'TEST DATA Backend Engineer' })).toBeInTheDocument()
    expect(requests[0].get('classification')).toBe('HIGH_MATCH,MEDIUM_MATCH,NEEDS_REVIEW')

    await user.click(within(screen.getByRole('group', { name: 'Clasificación' })).getByRole('button', { name: 'Requiere revisión' }))
    await waitFor(() => expect(requests[requests.length - 1].get('classification')).toBe('HIGH_MATCH,MEDIUM_MATCH'))

    await user.click(screen.getByRole('button', { name: 'Recalcular' }))
    expect(await screen.findByText('Se recalculó la compatibilidad de 42 ofertas')).toBeInTheDocument()

    expect(screen.getByText(/Cada evaluación usa el proveedor configurado y tiene costo/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Evaluar las mejores con IA' }))
    await waitFor(() => expect(topQuery).toBe('?limit=5'))
    expect(await screen.findByText('Se alcanzó el presupuesto mensual configurado para la IA.', {}, { timeout: 10_000 })).toBeInTheDocument()
  })

  it('explains how recommendations appear when there are none', async () => {
    server.use(http.get(`${API}/jobs/recommended`, () => HttpResponse.json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })))
    renderApp('/recommendations')
    expect(await screen.findByText('Todavía no hay recomendaciones')).toBeInTheDocument()
    expect(screen.getByText(/después de cada ejecución de tus búsquedas guardadas/)).toBeInTheDocument()
  })
})
