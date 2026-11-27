import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { API, problem } from '@/test/handlers'
import { jobDetail } from '@/test/fixtures2'
import { letter, letterSummary } from '@/test/fixtures3'
import { renderApp } from '@/test/render'
import { server } from '@/test/server'
import { removeSentence } from './labels'

describe('removeSentence', () => {
  it('removes a sentence and tidies whitespace', () => {
    expect(removeSentence('Hola. Frase dudosa. Chau.', 'Frase dudosa.')).toBe('Hola. Chau.')
    expect(removeSentence('Uno.\n\nFrase.\n\n\nDos.', 'Frase.')).toBe('Uno.\n\nDos.')
  })
})

function editorHandlers() {
  server.use(
    http.get(`${API}/cover-letters/40`, () => HttpResponse.json(letter)),
    http.get(`${API}/cover-letters/40/versions`, () => HttpResponse.json([letterSummary])),
    http.get(`${API}/cover-letters/:id/pdf`, () => new HttpResponse(new Uint8Array([37, 80, 68, 70]), { headers: { 'Content-Type': 'application/pdf' } })),
  )
}

describe('Editor de cartas', () => {
  it('removes a suggested sentence, warns about LinkedIn length and saves a new version', async () => {
    editorHandlers()
    let saved: { body: string } | null = null
    server.use(
      http.put(`${API}/cover-letters/40`, async ({ request }) => {
        saved = (await request.json()) as { body: string }
        return HttpResponse.json({ ...letter, id: 41, versionNo: 2, body: saved.body, suggestions: [] }, { status: 201 })
      }),
      http.get(`${API}/cover-letters/41`, () => HttpResponse.json({ ...letter, id: 41, versionNo: 2, suggestions: [] })),
      http.get(`${API}/cover-letters/41/versions`, () => HttpResponse.json([letterSummary])),
    )
    const { user } = renderApp('/cover-letters/40')
    const suggestions = await screen.findByRole('list', { name: 'Sugerencias' })
    expect(within(suggestions).getByText(/Menciona 'Kubernetes'/)).toBeInTheDocument()
    expect(screen.queryByLabelText('Asunto')).not.toBeInTheDocument()

    await user.click(within(suggestions).getByRole('button', { name: 'Quitar sugerencia 1' }))
    const body = screen.getByLabelText('Texto') as HTMLTextAreaElement
    expect(body.value).toBe('TEST DATA Hola, vi la oferta de Backend. Me encantaría conversar.')

    body.setSelectionRange(body.value.length, body.value.length)
    await user.click(body)
    await user.paste(' ' + 'x'.repeat(260))
    expect(screen.getByText(/supera el límite recomendado para mensaje de linkedin/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Guardar como nueva versión' }))
    await waitFor(() => expect(saved).not.toBeNull())
    expect(saved!.body.startsWith('TEST DATA Hola, vi la oferta de Backend. Me encantaría conversar. xxx')).toBe(true)
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/cover-letters/41'))
  })

  it('explains UNCONFIRMED_SUGGESTIONS on approve and copies to the clipboard', async () => {
    editorHandlers()
    server.use(http.post(`${API}/cover-letters/40/approve`, () => problem(400, 'UNCONFIRMED_SUGGESTIONS', 'pending')))
    const { user } = renderApp('/cover-letters/40')
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    await user.click(await screen.findByRole('button', { name: 'Aprobar' }))
    expect(await screen.findByText(/Esta versión tiene sugerencias sin revisar/)).toBeInTheDocument()
    // Drafts can be downloaded too, marked as "Borrador"
    await waitFor(() => expect(screen.getByRole('button', { name: /Descargar PDF \(Borrador\)/ })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: 'Copiar al portapapeles' }))
    expect(writeText).toHaveBeenCalledWith(letter.body)
  })
})

describe('Generar carta desde la oferta', () => {
  it('starts generation, polls the task and links to the new letter', async () => {
    let body: unknown = null
    let polls = 0
    server.use(
      http.get(`${API}/jobs/1`, () => HttpResponse.json(jobDetail)),
      http.post(`${API}/jobs/1/cover-letters`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ state: 'RUNNING', code: null, message: null }, { status: 202 })
      }),
      http.get(`${API}/jobs/1/cover-letters/task`, () => {
        polls++
        return HttpResponse.json({ state: polls === 1 ? 'RUNNING' : 'SUCCEEDED', code: null, message: null })
      }),
      http.get(`${API}/cover-letters`, ({ request }) => {
        expect(new URL(request.url).searchParams.get('jobId')).toBe('1')
        return HttpResponse.json({ content: [letterSummary], page: 0, size: 5, totalElements: 1, totalPages: 1 })
      }),
    )
    const { user } = renderApp('/jobs/1')
    await user.click(await screen.findByRole('button', { name: 'Generar carta' }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('combobox', { name: 'Formato' }))
    await user.click(await screen.findByRole('option', { name: 'Mensaje de LinkedIn' }))
    await user.click(within(dialog).getByRole('button', { name: 'Generar' }))
    await waitFor(() => expect(body).toEqual({ format: 'LINKEDIN_MESSAGE', language: 'es', tone: 'NEUTRAL' }))
    expect(await within(dialog).findByRole('link', { name: 'Abrir carta' }, { timeout: 10_000 })).toHaveAttribute('href', '/cover-letters/40')
  })
})

describe('Lista de cartas', () => {
  it('lists letters and filters by format on the server', async () => {
    const queries: URLSearchParams[] = []
    server.use(
      http.get(`${API}/cover-letters`, ({ request }) => {
        const q = new URL(request.url).searchParams
        queries.push(q)
        const all = [letterSummary, { ...letterSummary, id: 50, format: 'FORMAL_LETTER', subject: 'TEST DATA Carta formal' }]
        const content = all.filter((l) => !q.get('format') || l.format === q.get('format'))
        return HttpResponse.json({ content, page: 0, size: 50, totalElements: content.length, totalPages: 1 })
      }),
    )
    const { user } = renderApp('/cover-letters')
    expect(await screen.findByText('TEST DATA Carta formal')).toBeInTheDocument()
    await user.click(screen.getByRole('combobox', { name: 'Formato' }))
    await user.click(await screen.findByRole('option', { name: 'Mensaje de LinkedIn' }))
    await waitFor(() => expect(screen.queryByText('TEST DATA Carta formal')).not.toBeInTheDocument())
    expect(queries[queries.length - 1].get('format')).toBe('LINKEDIN_MESSAGE')
    expect(screen.getAllByRole('article')).toHaveLength(1)
  })
})
