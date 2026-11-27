import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { API } from '@/test/handlers'
import { upload } from '@/test/fixtures2'
import { renderApp } from '@/test/render'
import { server } from '@/test/server'

describe('Mejorar CV con IA', () => {
  it('starts the AI parse, polls and reloads the upload with the AI result', async () => {
    let started = false
    let polls = 0
    const aiUpload = {
      ...upload,
      parser: 'AI',
      parsed: { ...upload.parsed!, warnings: ['TEST DATA: advertencia del análisis con IA'] },
    }
    server.use(
      http.get(`${API}/resumes/uploads/77`, () => HttpResponse.json(started && polls >= 2 ? aiUpload : upload)),
      http.post(`${API}/resumes/uploads/77/ai-parse`, () => {
        started = true
        return HttpResponse.json({ state: 'RUNNING' }, { status: 202 })
      }),
      http.get(`${API}/resumes/uploads/77/ai-parse`, () => {
        if (!started) return HttpResponse.json({ state: 'NONE', code: null, message: null })
        polls++
        return HttpResponse.json({ state: polls === 1 ? 'RUNNING' : 'SUCCEEDED', code: null, message: null })
      }),
    )
    const { user } = renderApp('/cvs/uploads/77')
    expect(await screen.findByText(/sin correos, teléfonos ni enlaces/)).toBeInTheDocument()
    expect(screen.getByText('Análisis automático')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Mejorar con IA' }))
    expect(await screen.findByText('TEST DATA: advertencia del análisis con IA', {}, { timeout: 10_000 })).toBeInTheDocument()
    expect(screen.getByText('Análisis con IA')).toBeInTheDocument()
    expect(screen.getByText(/Se agregan con nivel «Solo proyectos» y Sin verificar/)).toBeInTheDocument()
  })
})
