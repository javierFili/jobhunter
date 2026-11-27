import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { API, problem } from '@/test/handlers'
import { db } from '@/test/db'
import { aiRoutes, promptVersions } from '@/test/fixtures2'
import { renderApp } from '@/test/render'
import { server } from '@/test/server'

const asAdmin = () => {
  db.user = { ...db.user, role: 'ADMIN' }
}

describe('Rutas de IA', () => {
  it('shows per-provider options, saves a route and explains a disabled provider (409)', async () => {
    asAdmin()
    const bodies: unknown[] = []
    server.use(
      http.get(`${API}/admin/ai/routes`, () => HttpResponse.json(aiRoutes)),
      http.get(`${API}/admin/ai/models`, ({ request }) =>
        HttpResponse.json({ provider: new URL(request.url).searchParams.get('provider'), models: ['deepseek-flash', 'deepseek-pro'], source: 'CONFIG' }),
      ),
      http.put(`${API}/admin/ai/routes/JOB_ANALYSIS`, async ({ request }) => {
        bodies.push(await request.json())
        if (bodies.length === 2) return problem(409, 'INTEGRATION_DISABLED', 'disabled')
        if (bodies.length === 3) return problem(409, 'CONFLICT', 'stale')
        return HttpResponse.json({ ...aiRoutes[0], model: 'deepseek-pro', version: 4 })
      }),
    )
    const { user } = renderApp('/admin/integrations?tab=ai-routes')
    expect(await screen.findByText(/DeepSeek almacena datos en China según su política de privacidad/)).toBeInTheDocument()
    const analysisForm = await screen.findByRole('form', { name: 'Ruta de IA: Análisis de ofertas' })
    expect(within(analysisForm).getByLabelText('Pensamiento')).toBeInTheDocument()
    expect(within(analysisForm).queryByLabelText('Esfuerzo')).not.toBeInTheDocument()
    expect(within(screen.getByRole('form', { name: 'Ruta de IA: Evaluación semántica' })).getByLabelText('Esfuerzo')).toBeInTheDocument()
    expect(screen.getByText('Fase 5')).toBeInTheDocument()

    const model = within(analysisForm).getByLabelText('Modelo')
    await user.clear(model)
    await user.type(model, 'deepseek-pro')
    await user.click(within(analysisForm).getByRole('button', { name: 'Guardar ruta' }))
    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toEqual({
      provider: 'DEEPSEEK',
      model: 'deepseek-pro',
      thinking: 'OFF',
      effort: null,
      fallbackProvider: null,
      fallbackModel: null,
      maxOutputTokens: 2000,
      version: 3,
    })
    expect(await screen.findByText('Ruta «Análisis de ofertas» guardada')).toBeInTheDocument()

    const form2 = screen.getByRole('form', { name: 'Ruta de IA: Análisis de ofertas' })
    await user.click(within(form2).getByRole('button', { name: 'Guardar ruta' }))
    expect(await screen.findByText(/El proveedor elegido está desactivado o no tiene credenciales/)).toBeInTheDocument()
    await user.click(within(screen.getByRole('form', { name: 'Ruta de IA: Análisis de ofertas' })).getByRole('button', { name: 'Guardar ruta' }))
    expect(await screen.findByText(/Otra persona modificó esta ruta/)).toBeInTheDocument()
  })
})

describe('Prompts', () => {
  it('lists versions, activates one and validates the output schema before saving', async () => {
    asAdmin()
    let activated: string | null = null
    let created: unknown = null
    server.use(
      http.get(`${API}/admin/prompts`, () => HttpResponse.json([promptVersions[0]])),
      http.get(`${API}/admin/prompts/JOB_ANALYSIS/versions`, () => HttpResponse.json(promptVersions)),
      http.post(`${API}/admin/prompts/JOB_ANALYSIS/versions/:v/activate`, ({ params }) => {
        activated = String(params.v)
        return HttpResponse.json({ ...promptVersions[1], active: true })
      }),
      http.post(`${API}/admin/prompts/JOB_ANALYSIS/versions`, async ({ request }) => {
        created = await request.json()
        return HttpResponse.json({ ...promptVersions[0], version: 3, active: false }, { status: 201 })
      }),
    )
    const { user } = renderApp('/admin/integrations?tab=prompts')
    const table = await screen.findByRole('table', { name: 'Versiones de Análisis de ofertas' })
    expect(within(table).getByText('Activa')).toBeInTheDocument()
    expect(screen.getByLabelText('Plantilla de sistema')).toHaveValue('TEST DATA system v2')

    await user.click(within(table).getByRole('button', { name: 'Activar versión 1' }))
    await waitFor(() => expect(activated).toBe('1'))

    const schema = screen.getByLabelText('Esquema de salida (JSON)')
    await user.clear(schema)
    await user.type(schema, '[[1]')
    await user.click(screen.getByRole('button', { name: 'Guardar como nueva versión' }))
    expect(await screen.findByText('Debe ser un objeto JSON válido')).toBeInTheDocument()
    expect(created).toBeNull()

    await user.clear(schema)
    await user.type(schema, '{{"type":"object"}')
    await user.click(screen.getByRole('button', { name: 'Guardar como nueva versión' }))
    await waitFor(() => expect(created).toMatchObject({ systemTemplate: 'TEST DATA system v2', outputSchema: { type: 'object' }, temperature: 0.1 }))
  })
})

describe('Consumo del mes', () => {
  it('shows per-integration requests, estimated cost and tokens', async () => {
    asAdmin()
    let range = ''
    server.use(
      http.get(`${API}/admin/integrations`, () => HttpResponse.json([])),
      http.get(`${API}/analytics/usage`, ({ request }) => {
        range = new URL(request.url).search
        return HttpResponse.json([
          { code: 'DEEPSEEK', requests: 12, costUsd: 0.0192, inputTokens: 14000, outputTokens: 3600, cachedTokens: 2000 },
          { code: 'REMOTIVE', requests: 30, costUsd: 0, inputTokens: 0, outputTokens: 0, cachedTokens: 0 },
        ])
      }),
    )
    renderApp('/admin/integrations')
    const table = await screen.findByRole('table', { name: 'Consumo del mes por integración' })
    expect(range).toMatch(/^\?from=\d{4}-\d{2}-01&to=\d{4}-\d{2}-\d{2}$/)
    expect(within(table).getByText('DEEPSEEK')).toBeInTheDocument()
    expect(within(table).getByText(/14\.000 \/ 3600 \/ 2000|14\.000 \/ 3\.600 \/ 2\.000/)).toBeInTheDocument()
    expect(screen.getByText(/Los costos son estimaciones/)).toBeInTheDocument()
  })
})
