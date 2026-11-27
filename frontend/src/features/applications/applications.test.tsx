import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { API, problem } from '@/test/handlers'
import { jobDetail, resume, versions } from '@/test/fixtures2'
import { app1, app2, board, preparePkg, transitions } from '@/test/fixtures3'
import { renderApp } from '@/test/render'
import { server } from '@/test/server'

function boardHandlers() {
  server.use(
    http.get(`${API}/applications/board`, () => HttpResponse.json(board())),
    http.get(`${API}/applications/transitions`, () => HttpResponse.json(transitions)),
  )
}

describe('Postulaciones — Kanban', () => {
  it('shows a column per status with cards, score chip and overdue follow-up', async () => {
    boardHandlers()
    renderApp('/applications')
    const ready = await screen.findByRole('region', { name: 'Columna Lista para postular' })
    const card = within(ready).getByRole('listitem', { name: 'TEST DATA Backend Engineer en TEST DATA — Example Corp' })
    expect(within(card).getByText(/Seguimiento vencido/)).toBeInTheDocument()
    expect(card).toHaveTextContent('Compatibilidad: 82 de 100')
    expect(screen.getAllByRole('region', { name: /^Columna / })).toHaveLength(11)
    expect(screen.getByText('JobHunter nunca envía nada por ti: envías tú y luego lo registras aquí.')).toBeInTheDocument()
  })

  it('moves a card with the accessible "Cambiar estado" menu (POST transition) and never offers APPLIED as a target', async () => {
    boardHandlers()
    let body: unknown = null
    server.use(
      http.post(`${API}/applications/2/transition`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ ...app2, status: 'READY_TO_APPLY' })
      }),
    )
    const { user } = renderApp('/applications')
    await user.click(await screen.findByRole('button', { name: 'Cambiar estado de TEST DATA Java Dev en TEST DATA — Otra Empresa' }))
    const menu = await screen.findByRole('menu')
    expect(within(menu).queryByRole('menuitem', { name: 'Postulada' })).not.toBeInTheDocument()
    await user.click(within(menu).getByRole('menuitem', { name: 'Lista para postular' }))
    await waitFor(() => expect(body).toEqual({ toStatus: 'READY_TO_APPLY' }))
  })

  it('records a manual submission with method and date ("Marcar como enviada")', async () => {
    boardHandlers()
    let body: { method: string; submittedAt: string } | null = null
    server.use(
      http.post(`${API}/applications/1/mark-submitted`, async ({ request }) => {
        body = (await request.json()) as { method: string; submittedAt: string }
        return HttpResponse.json({ ...app1, status: 'APPLIED', appliedAt: body.submittedAt })
      }),
    )
    const { user } = renderApp('/applications')
    await user.click(await screen.findByRole('button', { name: 'Cambiar estado de TEST DATA Backend Engineer en TEST DATA — Example Corp' }))
    await user.click(await screen.findByRole('menuitem', { name: 'Marcar como enviada' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/JobHunter nunca envía nada por ti/)).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Registrar envío' }))
    expect(await within(dialog).findByText('Indica cómo enviaste la postulación')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('combobox', { name: /¿Cómo la enviaste\?/ }))
    await user.click(await screen.findByRole('option', { name: 'Correo electrónico' }))
    await user.click(within(dialog).getByRole('button', { name: 'Registrar envío' }))
    await waitFor(() => expect(body).not.toBeNull())
    expect(body!.method).toBe('EMAIL')
    expect(body!.submittedAt).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/)
  })
})

describe('Postulaciones — Tabla', () => {
  it('filters by status', async () => {
    const queries: URLSearchParams[] = []
    server.use(
      http.get(`${API}/applications/board`, () => HttpResponse.json(board())),
      http.get(`${API}/applications/transitions`, () => HttpResponse.json(transitions)),
      http.get(`${API}/applications`, ({ request }) => {
        queries.push(new URL(request.url).searchParams)
        return HttpResponse.json({ content: [app1, app2], page: 0, size: 20, totalElements: 2, totalPages: 1 })
      }),
    )
    const { user } = renderApp('/applications?view=table')
    const table = await screen.findByRole('table', { name: 'Postulaciones' })
    expect(within(table).getByText('Lista para postular')).toBeInTheDocument()
    await user.click(within(screen.getByRole('group', { name: 'Filtrar por estado' })).getByRole('button', { name: 'Favorita' }))
    await waitFor(() => expect(queries[queries.length - 1].get('status')).toBe('FAVORITE'))
    expect(queries[queries.length - 1].get('sort')).toBe('updated')
  })
})

describe('Detalle de postulación', () => {
  it('picks a resume version and follow-up date (PATCH with version) and adds a note to the timeline', async () => {
    let patched: Record<string, unknown> | null = null
    let event: unknown = null
    server.use(
      http.get(`${API}/applications/1`, () => HttpResponse.json(app1)),
      http.get(`${API}/applications/transitions`, () => HttpResponse.json(transitions)),
      http.get(`${API}/applications/1/events`, () =>
        HttpResponse.json([{ id: 1, type: 'STATUS_CHANGE', fromStatus: 'FAVORITE', toStatus: 'READY_TO_APPLY', note: null, occurredAt: '2026-11-21T10:00:00Z' }]),
      ),
      http.get(`${API}/resumes`, () => HttpResponse.json([resume])),
      http.get(`${API}/resumes/5/versions`, () => HttpResponse.json(versions)),
      http.patch(`${API}/applications/1`, async ({ request }) => {
        patched = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...app1, ...patched, version: 3 })
      }),
      http.post(`${API}/applications/1/events`, async ({ request }) => {
        event = await request.json()
        return HttpResponse.json({ id: 2, type: 'NOTE', fromStatus: null, toStatus: null, note: 'x', occurredAt: '2026-11-27T10:00:00Z' })
      }),
    )
    const { user } = renderApp('/applications/1')
    expect(await screen.findByText('Cambio de estado: Favorita → Lista para postular')).toBeInTheDocument()
    const form = screen.getByRole('form', { name: 'Datos de la postulación' })
    await user.click(within(form).getByRole('combobox', { name: 'CV' }))
    await user.click(await screen.findByRole('option', { name: 'TEST DATA CV Backend' }))
    await user.click(within(form).getByRole('combobox', { name: 'Versión del CV' }))
    await user.click(await screen.findByRole('option', { name: 'v2' }))
    const follow = within(form).getByLabelText(/Próximo seguimiento/)
    await user.clear(follow)
    await user.type(follow, '2026-12-05')
    await user.click(within(form).getByRole('button', { name: 'Guardar cambios' }))
    await waitFor(() => expect(patched).not.toBeNull())
    expect(patched).toMatchObject({ resumeId: 5, resumeVersionNo: 2, followUpAt: '2026-12-05', version: 2 })

    const timeline = screen.getByRole('form', { name: 'Agregar al historial' })
    await user.type(within(timeline).getByLabelText('Detalle'), 'TEST DATA llamé al reclutador')
    await user.click(within(timeline).getByRole('button', { name: 'Agregar' }))
    await waitFor(() => expect(event).toEqual({ type: 'NOTE', note: 'TEST DATA llamé al reclutador' }))
  })
})

describe('Preparar postulación', () => {
  it('shows the package with copy buttons, checklist, warnings and CV download', async () => {
    let pdfRequested = false
    server.use(
      http.get(`${API}/applications/1`, () => HttpResponse.json(app1)),
      http.post(`${API}/applications/1/prepare`, () => HttpResponse.json(preparePkg)),
      http.get(`${API}/resumes/5/versions/2/pdf`, () => {
        pdfRequested = true
        return new HttpResponse(new Uint8Array([37, 80, 68, 70]), { headers: { 'Content-Type': 'application/pdf' } })
      }),
    )
    const { user } = renderApp('/applications/1/prepare')
    // user-event installs its own clipboard stub on setup; replace it afterwards.
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    expect(await screen.findByText('TEST DATA Postulación: Backend Engineer')).toBeInTheDocument()
    expect(screen.getByText('JobHunter nunca envía nada por ti: envías tú y luego lo registras aquí.')).toBeInTheDocument()
    expect(screen.getByText('TEST DATA: la carta no está aprobada')).toBeInTheDocument()
    const checklist = screen.getByRole('list', { name: 'Lista de verificación' })
    expect(checklist).toHaveTextContent('Hecho: CV aprobado')
    expect(checklist).toHaveTextContent('Pendiente: Carta revisada')
    expect(screen.getByRole('link', { name: /Abrir enlace para postular/ })).toHaveAttribute('href', 'https://example.test/apply/1')
    expect(screen.getByRole('button', { name: 'Marcar como enviada' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Copiar asunto' }))
    expect(writeText).toHaveBeenCalledWith('TEST DATA Postulación: Backend Engineer')
    await user.click(screen.getByRole('button', { name: 'Descargar CV en PDF' }))
    await waitFor(() => expect(pdfRequested).toBe(true))
  })

  it('from job detail: creates the application when none exists and opens the prepare view', async () => {
    let created: unknown = null
    server.use(
      http.get(`${API}/jobs/1`, () => HttpResponse.json(jobDetail)),
      http.get(`${API}/applications/by-job/1`, () => problem(404, 'NOT_FOUND', 'none')),
      http.post(`${API}/applications`, async ({ request }) => {
        created = await request.json()
        return HttpResponse.json(app1, { status: 201 })
      }),
      http.get(`${API}/applications/1`, () => HttpResponse.json(app1)),
      http.post(`${API}/applications/1/prepare`, () => HttpResponse.json(preparePkg)),
    )
    const { user } = renderApp('/jobs/1')
    await user.click(await screen.findByRole('button', { name: 'Preparar postulación' }))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/applications/1/prepare'))
    expect(created).toEqual({ jobOfferId: 1 })
  })
})
