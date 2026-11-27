import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { API, problem } from '@/test/handlers'
import { db } from '@/test/db'
import { adminSources, fxRates, integrations } from '@/test/fixtures2'
import { renderApp } from '@/test/render'
import { server } from '@/test/server'
import type { IntegrationDto } from './types'

const asAdmin = () => {
  db.user = { ...db.user, role: 'ADMIN' }
}

describe('Integraciones (admin)', () => {
  it('is not reachable nor listed for regular users', async () => {
    renderApp('/admin/integrations')
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/))
    expect(screen.queryByRole('link', { name: 'Integraciones' })).not.toBeInTheDocument()
  })

  it('groups by category, shows write-only credential state and the Apify note', async () => {
    asAdmin()
    server.use(http.get(`${API}/admin/integrations`, () => HttpResponse.json(integrations)))
    renderApp('/admin/integrations')
    expect(await screen.findByRole('heading', { name: 'IA' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Fuentes de empleo' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Scraping' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Integraciones' })).toBeInTheDocument()

    const deepseek = screen.getByRole('form', { name: 'Credenciales de DeepSeek' })
    const key = within(deepseek).getByLabelText(/API key/)
    expect(key).toHaveAttribute('type', 'password')
    expect(key).toHaveValue('')
    expect(within(deepseek).getByText('Configurada ••••ab12')).toBeInTheDocument()

    const claude = screen.getByRole('form', { name: 'Credenciales de Claude' })
    expect(within(claude).getByText('Definida por variable de entorno')).toBeInTheDocument()
    expect(within(claude).getByLabelText(/API key/)).toBeDisabled()

    expect(screen.getByText(/Desactivado por decisión del usuario: por ahora se busca solo en fuentes directas/)).toBeInTheDocument()
    expect(screen.getByText('Esta fuente es pública y no necesita credenciales.')).toBeInTheDocument()
  })

  it('explains MASTER_KEY_MISSING and clears the secret input after a successful save', async () => {
    asAdmin()
    const sent: unknown[] = []
    server.use(
      http.get(`${API}/admin/integrations`, () => HttpResponse.json(integrations)),
      http.put(`${API}/admin/integrations/DEEPSEEK/secret`, async ({ request }) => {
        sent.push(await request.json())
        if (sent.length === 1) return problem(409, 'MASTER_KEY_MISSING', 'Master key missing')
        const updated: IntegrationDto = {
          ...integrations[0],
          credentials: [{ ...integrations[0].credentials[0], last4: '9999' }],
        }
        return HttpResponse.json(updated)
      }),
    )
    const { user } = renderApp('/admin/integrations')
    const form = await screen.findByRole('form', { name: 'Credenciales de DeepSeek' })
    const key = within(form).getByLabelText(/API key/)
    await user.type(key, 'sk-test-TEST-DATA-9999')
    await user.click(within(form).getByRole('button', { name: 'Guardar credenciales' }))
    expect(await within(form).findByText(/JOBHUNTER_MASTER_KEY/)).toBeInTheDocument()

    await user.click(within(form).getByRole('button', { name: 'Guardar credenciales' }))
    await waitFor(() => expect(sent).toHaveLength(2))
    expect(sent[1]).toEqual({ apiKey: 'sk-test-TEST-DATA-9999' })
    await waitFor(() => expect(within(form).getByLabelText(/API key/)).toHaveValue(''))
    expect(within(form).getByText('Configurada ••••9999')).toBeInTheDocument()
  })

  it('explains CREDENTIALS_REQUIRED when enabling without credentials', async () => {
    asAdmin()
    let body: unknown = null
    server.use(
      http.get(`${API}/admin/integrations`, () => HttpResponse.json(integrations)),
      http.put(`${API}/admin/integrations/APIFY`, async ({ request }) => {
        body = await request.json()
        return problem(409, 'CREDENTIALS_REQUIRED', 'Credentials required')
      }),
    )
    const { user } = renderApp('/admin/integrations')
    await user.click(await screen.findByRole('switch', { name: 'Activar Apify' }))
    expect(await screen.findByText('Configura las credenciales obligatorias antes de activar esta integración.')).toBeInTheDocument()
    expect(body).toEqual({ enabled: true, version: 1 })
  })

  it('edits sources limits and FX rates', async () => {
    asAdmin()
    let sourceBody: unknown = null
    let fxBody: unknown = null
    server.use(
      http.get(`${API}/admin/sources`, () => HttpResponse.json(adminSources)),
      http.put(`${API}/admin/sources/remotive`, async ({ request }) => {
        sourceBody = await request.json()
        return HttpResponse.json({ ...adminSources[0], ...(sourceBody as object) })
      }),
      http.get(`${API}/admin/fx-rates`, () => HttpResponse.json(fxRates)),
      http.put(`${API}/admin/fx-rates`, async ({ request }) => {
        fxBody = await request.json()
        return HttpResponse.json((fxBody as { currency: string; rateToUsd: number }[]).map((r) => ({ ...r, asOf: '2026-11-26T00:00:00Z' })))
      }),
    )
    const { user } = renderApp('/admin/integrations?tab=sources')
    expect(await screen.findByText('Atribución obligatoria')).toBeInTheDocument()
    const cap = screen.getByLabelText('Peticiones/día')
    await user.clear(cap)
    await user.type(cap, '48')
    await user.click(screen.getByRole('button', { name: 'Guardar límites de Remotive' }))
    await waitFor(() => expect(sourceBody).toEqual({ minIntervalMinutes: 60, dailyRequestCap: 48 }))

    await user.click(screen.getByRole('tab', { name: 'Tipos de cambio' }))
    expect(await screen.findByText(/no se consulta ninguna API externa/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Agregar moneda' }))
    const currencies = screen.getAllByLabelText('Moneda')
    const rates = screen.getAllByLabelText('Valor en USD')
    await user.type(currencies[1], 'bob')
    await user.type(rates[1], '0.145')
    await user.click(screen.getByRole('button', { name: 'Guardar tipos de cambio' }))
    await waitFor(() =>
      expect(fxBody).toEqual([
        { currency: 'EUR', rateToUsd: 1.08 },
        { currency: 'BOB', rateToUsd: 0.145 },
      ]),
    )
  })
})
