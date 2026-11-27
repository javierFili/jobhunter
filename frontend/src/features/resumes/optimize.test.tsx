import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { API } from '@/test/handlers'
import { jobDetail, resume, resumeDoc, versions } from '@/test/fixtures2'
import { renderApp } from '@/test/render'
import { server } from '@/test/server'
import type { VersionSummaryDto } from './types'

const optimized: VersionSummaryDto = { ...versions[0], id: 60, versionNo: 3, parentVersionId: 51, origin: 'OPTIMIZED', jobOfferId: 1, note: null }

describe('Optimizar CV', () => {
  it('optimizes a resume for a job and links to the editor and the comparison', async () => {
    let body: unknown = null
    let polls = 0
    server.use(
      http.get(`${API}/jobs/1`, () => HttpResponse.json(jobDetail)),
      http.get(`${API}/resumes`, () => HttpResponse.json([resume])),
      http.post(`${API}/jobs/1/optimize-resume`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ state: 'RUNNING', code: null, message: null }, { status: 202 })
      }),
      http.get(`${API}/jobs/1/optimize-resume`, () => {
        polls++
        return HttpResponse.json(polls === 1 ? { task: { state: 'RUNNING', code: null, message: null }, version: null } : { task: { state: 'SUCCEEDED', code: null, message: null }, version: optimized })
      }),
    )
    const { user } = renderApp('/jobs/1')
    await user.click(await screen.findByRole('button', { name: 'Optimizar CV' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/El optimizador nunca inventa hechos/)).toBeInTheDocument()
    await user.click(within(dialog).getByRole('combobox', { name: /CV base/ }))
    await user.click(await screen.findByRole('option', { name: 'TEST DATA CV Backend' }))
    await user.click(within(dialog).getByRole('button', { name: 'Optimizar' }))
    await waitFor(() => expect(body).toEqual({ resumeId: 5, language: 'es' }))
    expect(await within(dialog).findByRole('link', { name: /Abrir en el editor/ }, { timeout: 10_000 })).toHaveAttribute('href', '/cvs/5?v=3')
    expect(within(dialog).getByRole('link', { name: /Comparar con la versión anterior/ })).toHaveAttribute('href', '/cvs/5?tab=compare&to=3&fromId=51')
  })

  it('shows the truthfulness report and the reason of suggested bullets in the editor', async () => {
    const doc = {
      ...resumeDoc,
      experience: [
        {
          ...resumeDoc.experience[0],
          bullets: [{ text: 'TEST DATA desplegué en Kubernetes', sourceRefs: [], status: 'SUGGESTED', note: "Menciona 'Kubernetes', que no está en tu perfil verificado" }],
        },
      ],
    }
    const list = [optimized, ...versions]
    let compareQuery = ''
    server.use(
      http.get(`${API}/resumes/5`, () => HttpResponse.json(resume)),
      http.get(`${API}/resumes/5/versions`, () => HttpResponse.json(list)),
      http.get(`${API}/resumes/5/versions/3`, () =>
        HttpResponse.json({
          summary: optimized,
          content: doc,
          validationReport: {
            provider: 'DEEPSEEK',
            model: 'deepseek-flash',
            jobOfferId: 1,
            groundedCount: 7,
            suggestedCount: 1,
            violations: [{ rule: 'UNVERIFIED_TECHNOLOGY', section: 'experience', ref: 'exp:1', text: 'Kubernetes', message: 'TEST DATA: tecnología fuera del perfil verificado' }],
          },
        }),
      ),
      http.get(`${API}/resumes/5/versions/3/pdf`, () => new HttpResponse(new Uint8Array([1]), { headers: { 'Content-Type': 'application/pdf' } })),
      http.get(`${API}/resumes/5/compare`, ({ request }) => {
        compareQuery = new URL(request.url).search
        return HttpResponse.json([])
      }),
    )
    const { user } = renderApp('/cvs/5?v=3')
    expect(await screen.findByRole('heading', { name: 'Verificación de veracidad' })).toBeInTheDocument()
    expect(screen.getByText('7 con fuente verificada')).toBeInTheDocument()
    expect(screen.getByText('1 sugerencias por revisar')).toBeInTheDocument()
    expect(screen.getByText(/Tecnología no verificada · experience/)).toBeInTheDocument()
    expect(screen.getByText("Menciona 'Kubernetes', que no está en tu perfil verificado")).toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: 'Comparar' }))
    await waitFor(() => expect(compareQuery).toBe('?from=2&to=3'))
  })

  it('opens the comparison against the parent version from the link parameters', async () => {
    let compareQuery = ''
    server.use(
      http.get(`${API}/resumes/5`, () => HttpResponse.json(resume)),
      http.get(`${API}/resumes/5/versions`, () => HttpResponse.json([optimized, ...versions])),
      http.get(`${API}/resumes/5/compare`, ({ request }) => {
        compareQuery = new URL(request.url).search
        return HttpResponse.json([])
      }),
    )
    renderApp('/cvs/5?tab=compare&to=3&fromId=51')
    await waitFor(() => expect(compareQuery).toBe('?from=1&to=3'))
  })
})
