import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { API, problem } from '@/test/handlers'
import { resume, resumeDoc, versions } from '@/test/fixtures2'
import { renderApp } from '@/test/render'
import { server } from '@/test/server'
import type { ResumeDocument, VersionSummaryDto } from './types'

function editorHandlers(extra: { onSave?: (body: { content: ResumeDocument; baseVersionNo: number; note?: string }) => void } = {}) {
  const list: VersionSummaryDto[] = [...versions]
  server.use(
    http.get(`${API}/resumes`, () => HttpResponse.json([resume])),
    http.get(`${API}/resumes/5`, () => HttpResponse.json(resume)),
    http.get(`${API}/resumes/5/versions`, () => HttpResponse.json(list)),
    http.get(`${API}/resumes/5/versions/:v`, ({ params }) => {
      const summary = list.find((v) => v.versionNo === Number(params.v))!
      return HttpResponse.json({ summary, content: resumeDoc, validationReport: null })
    }),
    http.get(`${API}/resumes/5/versions/:v/pdf`, () => new HttpResponse(new Uint8Array([37, 80, 68, 70]), { headers: { 'Content-Type': 'application/pdf' } })),
    http.post(`${API}/resumes/5/versions`, async ({ request }) => {
      const body = (await request.json()) as { content: ResumeDocument; baseVersionNo: number; note?: string }
      extra.onSave?.(body)
      const created: VersionSummaryDto = { ...versions[0], id: 53, versionNo: 3, parentVersionId: 52, note: body.note ?? null }
      list.unshift(created)
      return HttpResponse.json(created, { status: 201 })
    }),
  )
}

const owner = 'Backend Developer en TEST DATA — Example Corp'

describe('Editor de CV', () => {
  it('highlights suggested bullets, marks edits as USER_EDITED and saves a new version', async () => {
    let saved: { content: ResumeDocument; baseVersionNo: number; note?: string } | null = null
    editorHandlers({ onSave: (b) => (saved = b) })
    const { user } = renderApp('/cvs/5')

    const first = await screen.findByRole('textbox', { name: `Viñeta 1 de ${owner}` })
    const items = within(screen.getByRole('list', { name: `Viñetas de ${owner}` })).getAllByRole('listitem')
    expect(items[0]).toHaveAttribute('data-status', 'GROUNDED')
    expect(items[1]).toHaveAttribute('data-status', 'SUGGESTED')
    expect(within(items[1]).getByText('Sugerencia sin confirmar')).toBeInTheDocument()
    expect(screen.getByText('1 sugerencia sin confirmar')).toBeInTheDocument()
    expect(screen.getByText(/La fuente de verdad es/)).toBeInTheDocument()

    await user.type(first, ' (editada)')
    expect(items[0]).toHaveAttribute('data-status', 'USER_EDITED')
    await user.click(screen.getByRole('button', { name: `Confirmar Viñeta 2 de ${owner}` }))
    expect(items[1]).toHaveAttribute('data-status', 'USER_EDITED')
    expect(screen.queryByText(/sugerencia sin confirmar/)).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Guardar como nueva versión' }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText('Nota (opcional)'), 'TEST DATA ajuste')
    await user.click(within(dialog).getByRole('button', { name: 'Guardar versión' }))

    await waitFor(() => expect(saved).not.toBeNull())
    expect(saved!.baseVersionNo).toBe(2)
    expect(saved!.note).toBe('TEST DATA ajuste')
    expect(saved!.content.experience[0].bullets).toEqual([
      { text: 'TEST DATA viñeta basada en perfil (editada)', sourceRefs: ['exp:1'], status: 'USER_EDITED', note: null },
      { text: 'TEST DATA viñeta sugerida', sourceRefs: ['exp:1'], status: 'USER_EDITED', note: null },
    ])
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/cvs/5?v=3'))
  })

  it('removes a suggested bullet with "Eliminar"', async () => {
    editorHandlers()
    const { user } = renderApp('/cvs/5')
    await user.click(await screen.findByRole('button', { name: `Eliminar sugerencia Viñeta 2 de ${owner}` }))
    expect(within(screen.getByRole('list', { name: `Viñetas de ${owner}` })).getAllByRole('listitem')).toHaveLength(1)
  })

  it('explains UNCONFIRMED_SUGGESTIONS when approving', async () => {
    editorHandlers()
    server.use(http.post(`${API}/resumes/5/versions/2/approve`, () => problem(400, 'UNCONFIRMED_SUGGESTIONS', 'Unconfirmed')))
    const { user } = renderApp('/cvs/5')
    await user.click(await screen.findByRole('button', { name: 'Aprobar' }))
    expect(await screen.findByText('Confirma o elimina las sugerencias resaltadas en ámbar antes de aprobar.')).toBeInTheDocument()
  })

  it('shows the PDF preview from an authenticated blob', async () => {
    editorHandlers()
    renderApp('/cvs/5')
    const frame = await screen.findByTitle('Vista previa del CV en PDF')
    expect(frame.getAttribute('src')).toMatch(/^blob:/)
    expect(screen.getByRole('button', { name: 'Descargar PDF' })).toBeEnabled()
  })

  it('compares two versions grouped by section with added/removed lines', async () => {
    editorHandlers()
    let query = ''
    server.use(
      http.get(`${API}/resumes/5/compare`, ({ request }) => {
        query = new URL(request.url).search
        return HttpResponse.json([
          { op: 'SAME', section: 'summary', text: 'TEST DATA resumen' },
          { op: 'REMOVED', section: 'experience', text: 'TEST DATA viñeta vieja' },
          { op: 'ADDED', section: 'experience', text: 'TEST DATA viñeta nueva' },
        ])
      }),
    )
    const { user } = renderApp('/cvs/5')
    await user.click(await screen.findByRole('tab', { name: 'Comparar' }))
    const exp = await screen.findByRole('region', { name: 'Cambios en Experiencia' })
    expect(query).toBe('?from=1&to=2')
    expect(within(exp).getByText('TEST DATA viñeta nueva').closest('li')).toHaveAttribute('data-op', 'ADDED')
    expect(within(exp).getByText('TEST DATA viñeta vieja').closest('li')).toHaveAttribute('data-op', 'REMOVED')
    expect(within(exp).getByText('Agregado:')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Lado a lado' }))
    expect(within(exp).getByText('v1')).toBeInTheDocument()
    expect(within(exp).getByText('v2')).toBeInTheDocument()
  })

  it('lists versions with origin/status badges in Historial', async () => {
    editorHandlers()
    const { user } = renderApp('/cvs/5')
    await user.click(await screen.findByRole('tab', { name: 'Historial' }))
    const table = await screen.findByRole('table', { name: 'Historial de versiones' })
    expect(within(table).getByText('Edición manual')).toBeInTheDocument()
    expect(within(table).getByText('Desde perfil')).toBeInTheDocument()
    expect(within(table).getAllByText('Borrador')).toHaveLength(2)
  })
})

describe('Nuevo CV', () => {
  it('warns when unverified items were skipped and opens the editor', async () => {
    editorHandlers()
    let body: unknown = null
    server.use(
      http.post(`${API}/resumes`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ resume, version: versions[0], unverifiedItemsSkipped: 3 }, { status: 201 })
      }),
    )
    const { user } = renderApp('/cvs')
    await user.click(await screen.findByRole('button', { name: 'Nuevo CV' }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText(/Título/), 'TEST DATA CV Backend')
    await user.click(within(dialog).getByRole('button', { name: 'Crear CV' }))
    expect(await screen.findByText('3 elementos sin verificar no se incluyeron')).toBeInTheDocument()
    expect(body).toEqual({ profileId: null, title: 'TEST DATA CV Backend', language: 'es', templateId: 'classic' })
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/cvs/5'))
  })

  it('explains PROFILE_EMPTY', async () => {
    editorHandlers()
    server.use(http.post(`${API}/resumes`, () => problem(400, 'PROFILE_EMPTY', 'Profile empty')))
    const { user } = renderApp('/cvs')
    await user.click(await screen.findByRole('button', { name: 'Nuevo CV' }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText(/Título/), 'TEST DATA CV')
    await user.click(within(dialog).getByRole('button', { name: 'Crear CV' }))
    expect(await within(dialog).findByText(/Tu perfil no tiene datos verificados todavía/)).toBeInTheDocument()
  })
})
