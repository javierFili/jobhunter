import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { API, problem } from '@/test/handlers'
import { upload } from '@/test/fixtures2'
import { renderApp } from '@/test/render'
import { server } from '@/test/server'
import type { ImportRequest } from './types'

const pdf = () => new File(['%PDF-1.4 TEST DATA'], 'TEST-DATA-cv.pdf', { type: 'application/pdf' })

function uploadHandlers() {
  server.use(
    http.get(`${API}/resumes/uploads`, () => HttpResponse.json([])),
    http.get(`${API}/resumes`, () => HttpResponse.json([])),
    http.post(`${API}/resumes/uploads`, () => HttpResponse.json(upload, { status: 201 })),
    http.get(`${API}/resumes/uploads/77`, () => HttpResponse.json(upload)),
  )
}

describe('Importar CV', () => {
  it('rejects unsupported or too large files on the client', async () => {
    uploadHandlers()
    renderApp('/cvs?tab=import')
    const input = await screen.findByLabelText('Seleccionar archivo de CV')
    fireEvent.change(input, { target: { files: [new File(['x'], 'notas.txt', { type: 'text/plain' })] } })
    expect(await screen.findByText('Formato no soportado. Sube un archivo PDF o DOCX.')).toBeInTheDocument()
    const big = new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'grande.pdf', { type: 'application/pdf' })
    fireEvent.change(input, { target: { files: [big] } })
    expect(await screen.findByText('El archivo supera el máximo de 5 MB.')).toBeInTheDocument()
  })

  it('shows a Spanish message for server upload errors (NO_TEXT)', async () => {
    uploadHandlers()
    server.use(http.post(`${API}/resumes/uploads`, () => problem(400, 'NO_TEXT', 'No text')))
    const { user } = renderApp('/cvs?tab=import')
    await user.upload(await screen.findByLabelText('Seleccionar archivo de CV'), pdf())
    expect(await screen.findAllByText(/No se encontró texto en el archivo/)).not.toHaveLength(0)
  })

  it('uploads, requires the employment type and language level, and imports only selected items', async () => {
    uploadHandlers()
    let body: ImportRequest | null = null
    server.use(
      http.post(`${API}/resumes/uploads/77/import`, async ({ request }) => {
        body = (await request.json()) as ImportRequest
        return HttpResponse.json({ experiences: 1, projects: 0, educations: 1, certifications: 0, skills: 2, languages: 1 })
      }),
    )
    const { user } = renderApp('/cvs?tab=import')
    await user.upload(await screen.findByLabelText('Seleccionar archivo de CV'), pdf())

    expect(await screen.findByRole('heading', { name: 'Revisar CV importado' })).toBeInTheDocument()
    expect(screen.getByText(/Todo lo importado queda Sin verificar hasta que lo revises/)).toBeInTheDocument()
    expect(screen.getByText('TEST DATA: no se pudo leer la sección de certificaciones')).toBeInTheDocument()

    const exp = screen.getByRole('article', { name: 'TEST DATA Desarrollador · TEST DATA — Example Corp' })
    expect(within(exp).getByLabelText(/Fecha de inicio/)).toHaveValue('2020-03-01')

    // First attempt: employment type and language level missing
    await user.click(screen.getByRole('button', { name: 'Importar seleccionados' }))
    expect(await within(exp).findByText('Elige el tipo de empleo')).toBeInTheDocument()
    const lang = screen.getByRole('article', { name: 'Inglés' })
    expect(within(lang).getByText('Elige el nivel')).toBeInTheDocument()
    expect(body).toBeNull()

    await user.click(within(exp).getByRole('combobox', { name: /Tipo de empleo/ }))
    await user.click(await screen.findByRole('option', { name: 'Tiempo completo' }))
    await user.click(within(lang).getByRole('combobox', { name: /Nivel/ }))
    await user.click(await screen.findByRole('option', { name: 'B2 · Intermedio alto' }))
    // Do not import the project
    await user.click(screen.getByRole('checkbox', { name: 'Importar TEST DATA Proyecto' }))

    await user.click(screen.getByRole('button', { name: 'Importar seleccionados' }))
    await waitFor(() => expect(body).not.toBeNull())
    expect(body).toMatchObject({
      experiences: [
        {
          positionTitle: 'TEST DATA Desarrollador',
          companyName: 'TEST DATA — Example Corp',
          employmentType: 'FULL_TIME',
          startDate: '2020-03-01',
          endDate: '2022-06-30',
          current: false,
          achievements: ['TEST DATA logro'],
          skillIds: [101],
        },
      ],
      projects: [],
      educations: [{ institution: 'TEST DATA Universidad', startDate: '2014-01-01', endDate: '2019-01-01', status: 'COMPLETED' }],
      certifications: [],
      skillIds: [101, 102],
      languages: [{ languageCode: 'en', level: 'B2', certificate: null }],
    })
    expect(await screen.findByText('Se importaron 5 elementos')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ir a Mi perfil para verificar' })).toHaveAttribute('href', '/profile?tab=experience')
  })
})
