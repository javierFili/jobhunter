import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { API } from '@/test/handlers'
import { db } from '@/test/db'
import { renderApp } from '@/test/render'
import { server } from '@/test/server'

describe('Consentimientos de IA', () => {
  it('shows the four provider toggles with privacy notes and sends the full list on toggle', async () => {
    let sent: unknown = null
    server.use(
      http.put(`${API}/me/consents`, async ({ request }) => {
        sent = await request.json()
        const body = sent as { consentType: string; provider: string; granted: boolean }[]
        return HttpResponse.json(
          db.consents.map((c) => ({ ...c, granted: body.find((b) => b.consentType === c.consentType && b.provider === c.provider)!.granted })),
        )
      }),
    )
    const { user } = renderApp('/settings')
    const deepseekProfile = await screen.findByRole('switch', { name: 'Procesar mi perfil con IA con DeepSeek' })
    const claudeProfile = screen.getByRole('switch', { name: 'Procesar mi perfil con IA con Claude' })
    expect(screen.getAllByRole('switch')).toHaveLength(4)
    expect(deepseekProfile).not.toBeChecked()
    expect(claudeProfile).toBeChecked()
    expect(screen.getAllByText(/DeepSeek almacena los datos en servidores ubicados en China/)).toHaveLength(2)
    expect(screen.getAllByText(/Anthropic, empresa con sede en Estados Unidos/)).toHaveLength(2)
    expect(screen.getByText(/datos de contacto .* nunca se envían/)).toBeInTheDocument()

    await user.click(deepseekProfile)

    await waitFor(() =>
      expect(sent).toEqual([
        { consentType: 'AI_PROCESS_PROFILE', provider: 'deepseek', granted: true },
        { consentType: 'AI_PROCESS_PROFILE', provider: 'claude', granted: true },
        { consentType: 'AI_PROCESS_CV', provider: 'deepseek', granted: false },
        { consentType: 'AI_PROCESS_CV', provider: 'claude', granted: false },
      ]),
    )
    await waitFor(() => expect(screen.getByRole('switch', { name: 'Procesar mi perfil con IA con DeepSeek' })).toBeChecked())
  })
})

describe('Tema', () => {
  it('switches to dark mode, persists in localStorage and on the server', async () => {
    let patched: unknown = null
    server.use(
      http.patch(`${API}/me`, async ({ request }) => {
        patched = await request.json()
        db.user = { ...db.user, ...(patched as object) }
        return HttpResponse.json(db.user)
      }),
    )
    const { user } = renderApp('/')
    await user.click(await screen.findByRole('button', { name: /Cambiar tema/ }))
    const menu = await screen.findByRole('menu')
    await user.click(within(menu).getByRole('menuitemradio', { name: 'Oscuro' }))

    expect(document.documentElement).toHaveClass('dark')
    expect(localStorage.getItem('jh-theme')).toBe('dark')
    await waitFor(() => expect(patched).toEqual({ theme: 'dark' }))

    await user.click(screen.getByRole('button', { name: /Cambiar tema/ }))
    await user.click(within(await screen.findByRole('menu')).getByRole('menuitemradio', { name: 'Claro' }))
    expect(document.documentElement).not.toHaveClass('dark')
    expect(localStorage.getItem('jh-theme')).toBe('light')
  })

  it('applies the theme stored on the server when the session is restored', async () => {
    db.user = { ...db.user, theme: 'dark' }
    renderApp('/')
    await waitFor(() => expect(document.documentElement).toHaveClass('dark'))
  })

  it('keeps the local choice when the server cannot save it (localStorage fallback)', async () => {
    server.use(http.patch(`${API}/me`, () => HttpResponse.json({ status: 500 }, { status: 500 })))
    const { user } = renderApp('/')
    await user.click(await screen.findByRole('button', { name: /Cambiar tema/ }))
    await user.click(within(await screen.findByRole('menu')).getByRole('menuitemradio', { name: 'Oscuro' }))
    expect(document.documentElement).toHaveClass('dark')
    expect(localStorage.getItem('jh-theme')).toBe('dark')
  })
})
