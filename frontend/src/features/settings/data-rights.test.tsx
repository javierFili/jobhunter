import { screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { API, problem } from '@/test/handlers'
import { renderApp } from '@/test/render'
import { server } from '@/test/server'

describe('Mis datos', () => {
  it('downloads the export as a ZIP', async () => {
    let exported = false
    server.use(
      http.get(`${API}/me/export`, () => {
        exported = true
        return new HttpResponse(new Uint8Array([80, 75, 3, 4]), { headers: { 'Content-Type': 'application/zip' } })
      }),
    )
    const { user } = renderApp('/settings')
    await user.click(await screen.findByRole('button', { name: 'Exportar mis datos' }))
    await waitFor(() => expect(exported).toBe(true))
    expect(await screen.findByText('Exportación descargada')).toBeInTheDocument()
  })

  it('requires typing ELIMINAR and the password, then deletes the account and logs out', async () => {
    const bodies: unknown[] = []
    server.use(
      http.delete(`${API}/me`, async ({ request }) => {
        bodies.push(await request.json())
        if (bodies.length === 1) return problem(400, 'INVALID_CREDENTIALS', 'bad password')
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { user } = renderApp('/settings')
    const button = await screen.findByRole('button', { name: 'Eliminar mi cuenta definitivamente' })
    expect(screen.getByText(/irreversible/)).toBeInTheDocument()
    expect(button).toBeDisabled()
    await user.type(screen.getByLabelText('Escribe ELIMINAR para confirmar'), 'eliminar')
    await user.type(screen.getByLabelText('Tu contraseña'), 'wrong-password')
    expect(button).toBeDisabled()
    await user.clear(screen.getByLabelText('Escribe ELIMINAR para confirmar'))
    await user.type(screen.getByLabelText('Escribe ELIMINAR para confirmar'), 'ELIMINAR')
    await user.click(button)
    expect(await screen.findByText('La contraseña no es correcta.')).toBeInTheDocument()

    await user.click(button)
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login'))
    expect(bodies).toEqual([{ password: 'wrong-password' }, { password: 'wrong-password' }])
    // the wrong-password 401 must not have triggered a refresh + retry
    expect(bodies).toHaveLength(2)
  })
})
