import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { API } from '@/test/handlers'
import { dashboardSummary } from '@/test/fixtures'
import { server } from '@/test/server'
import { db } from '@/test/db'
import { describe, expect, it } from 'vitest'
import { renderApp } from '@/test/render'

describe('Panel', () => {
  it('shows completeness, missing sections with links, counters and the applications summary', async () => {
    renderApp('/')
    expect(await screen.findByText('40%')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Agrega un titular/ })).toHaveAttribute('href', '/profile?tab=profiles')
    expect(screen.getByRole('link', { name: /Indica los idiomas/ })).toHaveAttribute('href', '/profile?tab=languages')
    const counters = screen.getByRole('region', { name: 'Resumen de tu perfil' })
    expect(within(counters).getByRole('link', { name: /4\s*Proyectos/ })).toBeInTheDocument()
    expect(screen.getByText('Aún no hay ofertas')).toBeInTheDocument()
    expect(await screen.findByRole('list', { name: 'Resumen de postulaciones' })).toBeInTheDocument()
  })

  it('hides the admin-only Integraciones item for regular users and navigates to other sections', async () => {
    const { user } = renderApp('/')
    const nav = await screen.findByRole('link', { name: 'Cartas de presentación' })
    expect(screen.queryByRole('link', { name: 'Integraciones' })).not.toBeInTheDocument()
    await user.click(nav)
    expect(await screen.findByRole('heading', { name: 'Cartas de presentación' })).toBeInTheDocument()
  })

  it('shows job and saved-search stats when the summary includes them', async () => {
    server.use(
      http.get(`${API}/dashboard/summary`, () =>
        HttpResponse.json({
          ...dashboardSummary,
          jobs: { total: 120, newLast7Days: 15, eligibleOrLikely: 42, saved: 3 },
          searches: { count: 2, lastRunAt: null },
        }),
      ),
    )
    renderApp('/')
    const list = await screen.findByRole('list', { name: 'Resumen de ofertas' })
    expect(within(list).getByRole('link', { name: /42\s*Accesibles o probables desde Bolivia/ })).toHaveAttribute('href', '/jobs?eligibility=ELIGIBLE,LIKELY')
    expect(within(list).getByRole('link', { name: /120\s*Ofertas en total/ })).toBeInTheDocument()
    expect(screen.getByText(/2 búsquedas guardadas · aún sin ejecutar/)).toBeInTheDocument()
  })

  it('shows the monthly usage card only to admins', async () => {
    renderApp('/')
    await screen.findByText('40%')
    expect(screen.queryByRole('heading', { name: 'Consumo del mes' })).not.toBeInTheDocument()
  })

  it('shows the monthly usage card to admins with estimated costs', async () => {
    db.user = { ...db.user, role: 'ADMIN' }
    server.use(
      http.get(`${API}/analytics/usage`, () =>
        HttpResponse.json([{ code: 'DEEPSEEK', requests: 5, costUsd: 0.008, inputTokens: 1000, outputTokens: 200, cachedTokens: 0 }]),
      ),
    )
    renderApp('/')
    expect(await screen.findByRole('heading', { name: 'Consumo del mes' })).toBeInTheDocument()
    expect(await screen.findByRole('table', { name: 'Consumo del mes por integración' })).toBeInTheDocument()
    expect(screen.getByText(/Los costos son estimaciones/)).toBeInTheDocument()
  })
})
