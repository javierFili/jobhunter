import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { API } from '@/test/handlers'
import { jobDetail, jobs } from '@/test/fixtures2'
import { renderApp } from '@/test/render'
import { server } from '@/test/server'

function jobsHandler(requests: URLSearchParams[] = []) {
  server.use(
    http.get(`${API}/jobs`, ({ request }) => {
      requests.push(new URL(request.url).searchParams)
      return HttpResponse.json({ content: jobs, page: 0, size: 20, totalElements: jobs.length, totalPages: 1 })
    }),
  )
  return requests
}

describe('Descubrir empleos', () => {
  it('shows the Bolivia eligibility badge per job; UNKNOWN is never shown as eligible', async () => {
    jobsHandler()
    renderApp('/jobs')
    const expected: Record<string, string> = {
      'TEST DATA Backend Engineer': 'Accesible desde Bolivia',
      'TEST DATA Laravel Developer': 'Probablemente accesible',
      'TEST DATA Java Dev (US only)': 'No accesible desde Bolivia',
      'TEST DATA Platform Engineer': 'Requiere revisión',
    }
    for (const [title, label] of Object.entries(expected)) {
      const card = await screen.findByRole('article', { name: title })
      expect(within(card).getByText(label)).toBeInTheDocument()
    }
    const unknown = screen.getByRole('article', { name: 'TEST DATA Platform Engineer' })
    expect(within(unknown).queryByText(/Accesible desde Bolivia|Probablemente accesible/)).not.toBeInTheDocument()
    expect(within(unknown).getByText('Vía Himalayas')).toBeInTheDocument()
    const link = within(screen.getByRole('article', { name: 'TEST DATA Backend Engineer' })).getByRole('link', { name: /Vía Remotive/ })
    expect(link).toHaveAttribute('href', 'https://example.test/remotive/1')
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', 'noopener noreferrer')
    expect(screen.getByText('4 ofertas encontradas')).toBeInTheDocument()
  })

  it('sends filters as comma-separated query params', async () => {
    const requests = jobsHandler()
    const { user } = renderApp('/jobs')
    await screen.findByRole('article', { name: 'TEST DATA Backend Engineer' })
    await user.type(screen.getByLabelText('Palabras clave'), 'laravel')
    await user.click(screen.getByRole('button', { name: /Más filtros/ }))
    const eligibility = screen.getByRole('group', { name: 'Accesibilidad desde Bolivia' })
    await user.click(within(eligibility).getByRole('button', { name: 'Accesible desde Bolivia' }))
    await user.click(within(eligibility).getByRole('button', { name: 'Probablemente accesible' }))
    await user.click(within(screen.getByRole('group', { name: 'Modalidad' })).getByRole('button', { name: 'Remoto' }))
    await user.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
    await waitFor(() => {
      const last = requests[requests.length - 1]
      expect(last.get('q')).toBe('laravel')
      expect(last.get('eligibility')).toBe('ELIGIBLE,LIKELY')
      expect(last.get('modalities')).toBe('REMOTE')
      expect(last.get('page')).toBe('0')
      expect(last.get('sort')).toBe('recent')
    })
    expect(screen.getByTestId('location')).toHaveTextContent('eligibility=ELIGIBLE%2CLIKELY')
  })

  it('saves a job via PUT /jobs/{id}/state', async () => {
    jobsHandler()
    let body: unknown = null
    server.use(
      http.put(`${API}/jobs/1/state`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ saved: true, hidden: false })
      }),
    )
    const { user } = renderApp('/jobs')
    const card = await screen.findByRole('article', { name: 'TEST DATA Backend Engineer' })
    await user.click(within(card).getByRole('button', { name: 'Guardar' }))
    await waitFor(() => expect(body).toEqual({ saved: true }))
    expect(await within(card).findByRole('button', { name: 'Guardada' })).toHaveAttribute('aria-pressed', 'true')
  })
})

describe('Detalle de oferta', () => {
  it('renders attribution links, the original offer link and the job actions', async () => {
    server.use(http.get(`${API}/jobs/1`, () => HttpResponse.json(jobDetail)))
    renderApp('/jobs/1')
    expect(await screen.findByRole('heading', { level: 1, name: 'TEST DATA Backend Engineer' })).toBeInTheDocument()

    const attributions = screen.getAllByRole('link', { name: /Vía Remotive/ })
    for (const a of attributions) {
      expect(a).toHaveAttribute('href', 'https://example.test/remotive/1')
      expect(a).toHaveAttribute('target', '_blank')
      expect(a).toHaveAttribute('rel', 'noopener noreferrer')
    }
    const apply = screen.getByRole('link', { name: /Abrir oferta original/ })
    expect(apply).toHaveAttribute('href', 'https://example.test/apply/1')
    expect(apply).toHaveAttribute('rel', 'noopener noreferrer')

    for (const label of ['Optimizar CV', 'Generar carta', 'Preparar postulación']) {
      expect(screen.getByRole('button', { name: label })).toBeEnabled()
    }
    expect(screen.getByRole('button', { name: 'Analizar' })).toBeEnabled()
    expect(screen.getByText('descripción', { selector: 'strong' })).toBeInTheDocument()
    expect(screen.getAllByText('TEST DATA: «Open to candidates in LATAM».').length).toBeGreaterThan(0)
  })
})

describe('Notificaciones', () => {
  it('shows the unread count and marks all as read', async () => {
    let readAll = false
    server.use(
      http.get(`${API}/notifications/unread-count`, () => HttpResponse.json({ count: readAll ? 0 : 1 })),
      http.get(`${API}/notifications`, () =>
        HttpResponse.json({
          content: [{ id: 1, type: 'NEW_JOBS', title: 'TEST DATA 3 ofertas nuevas', body: null, link: '/searches/9', createdAt: '2026-11-25T10:00:00Z', readAt: null }],
          page: 0,
          size: 15,
          totalElements: 1,
          totalPages: 1,
        }),
      ),
      http.post(`${API}/notifications/read-all`, () => {
        readAll = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { user } = renderApp('/')
    await user.click(await screen.findByRole('button', { name: 'Notificaciones: 1 sin leer' }))
    expect(await screen.findByText('TEST DATA 3 ofertas nuevas')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Marcar todas como leídas/ }))
    await waitFor(() => expect(readAll).toBe(true))
    expect(await screen.findByRole('button', { name: 'Notificaciones' })).toBeInTheDocument()
  })
})
