import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { API, problem } from '@/test/handlers'
import { jobs, savedSearch, watchlist } from '@/test/fixtures2'
import { renderApp } from '@/test/render'
import { server } from '@/test/server'
import type { SavedSearchRequest } from './types'

const emptyPage = { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 }

describe('Búsquedas guardadas', () => {
  it('prefills from a quick template and creates the search', async () => {
    let body: SavedSearchRequest | null = null
    server.use(
      http.get(`${API}/searches`, () => HttpResponse.json([])),
      http.post(`${API}/searches`, async ({ request }) => {
        body = (await request.json()) as SavedSearchRequest
        return HttpResponse.json({ ...savedSearch, ...body, id: 10 }, { status: 201 })
      }),
    )
    const { user } = renderApp('/searches')
    await user.click((await screen.findAllByRole('button', { name: 'Nueva búsqueda' }))[0])
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Backend LATAM' }))
    expect(within(dialog).getByLabelText(/Nombre/)).toHaveValue('Backend LATAM')
    // Unusable sources are disabled and explain why
    expect(within(dialog).getByRole('checkbox', { name: /Adzuna/ })).toBeDisabled()
    expect(within(dialog).getByText('No disponible: Faltan credenciales')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('checkbox', { name: /Remotive/ }))
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(body).not.toBeNull())
    expect(body).toMatchObject({
      name: 'Backend LATAM',
      keywords: 'backend',
      sourceCodes: ['remotive'],
      scheduleType: 'DAILY',
      dailyTime: '08:00',
      timezone: 'America/La_Paz',
      intervalMinutes: null,
      enabled: true,
      maxResultsPerSource: 50,
      notifyOnNew: true,
    })
    expect(body!.filters.regions).toEqual(['LATAM'])
    expect(body!.filters.eligibility).toEqual(['ELIGIBLE', 'LIKELY'])
  })

  it('validates the interval (≥ 60 minutes) and max results', async () => {
    server.use(http.get(`${API}/searches`, () => HttpResponse.json([])))
    const { user } = renderApp('/searches')
    await user.click((await screen.findAllByRole('button', { name: 'Nueva búsqueda' }))[0])
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText(/Nombre/), 'TEST DATA búsqueda')
    await user.click(within(dialog).getByRole('radio', { name: 'Cada cierto tiempo' }))
    const interval = within(dialog).getByLabelText(/Cada \(minutos\)/)
    await user.clear(interval)
    await user.type(interval, '30')
    const max = within(dialog).getByLabelText(/Máx. resultados por fuente/)
    await user.clear(max)
    await user.type(max, '500')
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }))
    expect(await within(dialog).findByText('El intervalo mínimo es de 60 minutos')).toBeInTheDocument()
    expect(within(dialog).getByText('Máximo 100')).toBeInTheDocument()
  })

  it('runs a search and polls the execution until it finishes', async () => {
    let polls = 0
    server.use(
      http.get(`${API}/searches`, () => HttpResponse.json([savedSearch])),
      http.get(`${API}/searches/9`, () => HttpResponse.json(savedSearch)),
      http.post(`${API}/searches/9/run`, () => HttpResponse.json({ executionId: 100, status: 'RUNNING' }, { status: 202 })),
      http.get(`${API}/searches/9/executions`, () => HttpResponse.json(emptyPage)),
      http.get(`${API}/searches/executions/100`, () => {
        polls++
        const running = polls === 1
        return HttpResponse.json({
          id: 100,
          trigger: 'MANUAL',
          status: running ? 'RUNNING' : 'SUCCEEDED',
          startedAt: '2026-11-26T10:00:00Z',
          finishedAt: running ? null : '2026-11-26T10:00:05Z',
          jobsFound: running ? 0 : 4,
          jobsNew: running ? 0 : 2,
          jobsDuplicate: running ? 0 : 1,
          costUsd: 0,
          errorSummary: null,
          sources: [
            { sourceCode: 'remotive', status: running ? 'RUNNING' : 'SUCCEEDED', requestCount: 1, itemsFetched: 4, itemsNew: 2, itemsDuplicate: 1, costUsd: 0, errorMessage: null, startedAt: null, finishedAt: null },
            { sourceCode: 'adzuna', status: 'SKIPPED_DISABLED', requestCount: 0, itemsFetched: 0, itemsNew: 0, itemsDuplicate: 0, costUsd: 0, errorMessage: null, startedAt: null, finishedAt: null },
          ],
        })
      }),
      http.get(`${API}/searches/executions/100/jobs`, ({ request }) => {
        expect(new URL(request.url).searchParams.get('onlyNew')).toBe('true')
        return HttpResponse.json({ content: jobs.slice(0, 2), page: 0, size: 10, totalElements: 2, totalPages: 1 })
      }),
    )
    const { user } = renderApp('/searches')
    const card = await screen.findByRole('article', { name: 'Búsqueda TEST DATA Backend LATAM' })
    await user.click(within(card).getByRole('button', { name: 'Ejecutar ahora' }))

    expect(await screen.findByText(/Buscando en las fuentes/)).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/searches/9?execution=100')
    // polls every 3 s until SUCCEEDED
    expect(await screen.findByText('4 ofertas encontradas · 2 nuevas · 1 duplicadas', {}, { timeout: 10_000 })).toBeInTheDocument()
    expect(polls).toBeGreaterThanOrEqual(2)
    expect(screen.getByText('Omitida: fuente desactivada')).toBeInTheDocument()
    expect(await screen.findByRole('article', { name: 'TEST DATA Laravel Developer' })).toBeInTheDocument()
  })

  it('explains ALREADY_RUNNING', async () => {
    server.use(
      http.get(`${API}/searches`, () => HttpResponse.json([savedSearch])),
      http.post(`${API}/searches/9/run`, () => problem(409, 'ALREADY_RUNNING', 'Already running')),
    )
    const { user } = renderApp('/searches')
    const card = await screen.findByRole('article', { name: 'Búsqueda TEST DATA Backend LATAM' })
    await user.click(within(card).getByRole('button', { name: 'Ejecutar ahora' }))
    expect(await screen.findByText('Esta búsqueda ya se está ejecutando. Espera a que termine.')).toBeInTheDocument()
  })
})

describe('Empresas vigiladas', () => {
  it('validates the board token locally and against the server', async () => {
    let validated: unknown = null
    server.use(
      http.get(`${API}/searches`, () => HttpResponse.json([])),
      http.get(`${API}/watchlist`, () => HttpResponse.json(watchlist)),
      http.post(`${API}/watchlist/validate`, async ({ request }) => {
        validated = await request.json()
        return HttpResponse.json({ ok: true, jobCount: 7, message: 'OK' })
      }),
    )
    const { user } = renderApp('/searches?tab=watchlist')
    expect(await screen.findByText('TEST DATA — Example Corp')).toBeInTheDocument()
    const token = screen.getByLabelText(/Identificador del tablero/)
    await user.type(token, 'https://jobs.lever.co/acme')
    await user.click(screen.getByRole('button', { name: 'Validar' }))
    expect(await screen.findByText(/Usa solo el último segmento de la URL/)).toBeInTheDocument()
    expect(validated).toBeNull()

    await user.clear(token)
    await user.type(token, 'acme')
    await user.click(screen.getByRole('button', { name: 'Validar' }))
    expect(await screen.findByText('Tablero válido: 7 ofertas publicadas.')).toBeInTheDocument()
    expect(validated).toEqual({ ats: 'GREENHOUSE', boardToken: 'acme' })
  })
})
