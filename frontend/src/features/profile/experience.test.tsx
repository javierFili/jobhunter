import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { ExperienceRequest } from '@/lib/types'
import { API } from '@/test/handlers'
import { renderApp } from '@/test/render'
import { server } from '@/test/server'

describe('Experiencia laboral', () => {
  it('lists existing experience with verified badge', async () => {
    renderApp('/profile?tab=experience')
    const card = await screen.findByRole('article', { name: 'Backend Developer en TEST DATA — Example Corp' })
    expect(within(card).getByText('Tiempo completo')).toBeInTheDocument()
    expect(within(card).getByText('Verificado')).toBeInTheDocument()
  })

  it('validates required fields', async () => {
    const { user } = renderApp('/profile?tab=experience')
    await user.click(await screen.findByRole('button', { name: 'Agregar experiencia' }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }))
    expect(await within(dialog).findByText('Ingresa el nombre de la empresa')).toBeInTheDocument()
    expect(within(dialog).getByText('Ingresa el cargo')).toBeInTheDocument()
    expect(within(dialog).getByText('Ingresa la fecha de inicio')).toBeInTheDocument()
  })

  it('creates an experience with skills chosen from the catalog', async () => {
    let posted: ExperienceRequest | null = null
    server.events.on('request:start', async ({ request }) => {
      if (request.method === 'POST' && request.url.endsWith('/api/v1/experiences')) {
        posted = (await request.clone().json()) as ExperienceRequest
      }
    })
    const { user } = renderApp('/profile?tab=experience')
    await user.click(await screen.findByRole('button', { name: 'Agregar experiencia' }))
    const dialog = await screen.findByRole('dialog')

    await user.type(within(dialog).getByLabelText(/Empresa/), 'TEST DATA — Example Corp Dos')
    await user.type(within(dialog).getByLabelText(/Cargo/), 'Full Stack Developer')
    await user.type(within(dialog).getByLabelText(/Fecha de inicio/), '2020-01-15')
    await user.type(within(dialog).getByLabelText(/Fecha de fin/), '2020-12-31')

    // pick two skills through the autocomplete
    await user.click(within(dialog).getByRole('combobox', { name: 'Habilidades usadas' }))
    await user.type(await screen.findByLabelText('Buscar habilidad'), 'react')
    await user.click(await screen.findByRole('option', { name: /TEST DATA React/ }))
    await user.click(within(dialog).getByRole('combobox', { name: 'Habilidades usadas' }))
    await user.type(await screen.findByLabelText('Buscar habilidad'), 'postgre')
    await user.click(await screen.findByRole('option', { name: /TEST DATA PostgreSQL/ }))

    const chosen = within(dialog).getByRole('list', { name: 'Habilidades seleccionadas' })
    expect(within(chosen).getByText('TEST DATA React')).toBeInTheDocument()
    expect(within(chosen).getByText('TEST DATA PostgreSQL')).toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(posted).toMatchObject({
      companyName: 'TEST DATA — Example Corp Dos',
      positionTitle: 'Full Stack Developer',
      employmentType: 'FULL_TIME',
      workModality: 'REMOTE',
      startDate: '2020-01-15',
      endDate: '2020-12-31',
      current: false,
      companyUrl: null,
      skillIds: [102, 103],
    })
    const card = await screen.findByRole('article', { name: 'Full Stack Developer en TEST DATA — Example Corp Dos' })
    expect(within(card).getByText('Sin verificar')).toBeInTheDocument()
    expect(within(card).getByText('TEST DATA PostgreSQL')).toBeInTheDocument()
    server.events.removeAllListeners()
  })

  it('rejects an end date before the start date', async () => {
    const { user } = renderApp('/profile?tab=experience')
    await user.click(await screen.findByRole('button', { name: 'Agregar experiencia' }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText(/Empresa/), 'TEST DATA Corp')
    await user.type(within(dialog).getByLabelText(/Cargo/), 'Dev')
    await user.type(within(dialog).getByLabelText(/Fecha de inicio/), '2022-05-01')
    await user.type(within(dialog).getByLabelText(/Fecha de fin/), '2021-05-01')
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }))
    expect(await within(dialog).findByText('La fecha de fin debe ser posterior a la de inicio')).toBeInTheDocument()
  })

  it('bulk-verifies selected items via POST /profile/verify', async () => {
    let body: unknown = null
    server.use(
      http.post(`${API}/profile/verify`, async ({ request }) => {
        body = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { user } = renderApp('/profile?tab=experience')
    await user.click(await screen.findByRole('checkbox', { name: /Seleccionar Backend Developer/ }))
    await user.click(screen.getByRole('button', { name: /Marcar como verificado \(1\)/ }))
    await waitFor(() =>
      expect(body).toEqual({
        experienceIds: [1],
        projectIds: [],
        educationIds: [],
        certificationIds: [],
        skillIds: [],
        languageCodes: [],
      }),
    )
  })
})
