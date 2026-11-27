import { screen } from '@testing-library/react'
import { http } from 'msw'
import { describe, expect, it } from 'vitest'
import { API, problem } from '@/test/handlers'
import { renderApp } from '@/test/render'
import { server } from '@/test/server'

describe('register — error codes', () => {
  it('maps 409 EMAIL_TAKEN (without errors[]) to the email field', async () => {
    server.use(
      http.post(`${API}/auth/refresh`, () => problem(401, 'INVALID_REFRESH_TOKEN', 'Sin sesión')),
      http.post(`${API}/auth/register`, () => problem(409, 'EMAIL_TAKEN', 'El correo ya está registrado')),
    )
    const { user } = renderApp('/register')
    await user.type(await screen.findByLabelText('Nombre completo'), 'TEST DATA Persona')
    await user.type(screen.getByLabelText('Correo electrónico'), 'test.taken@example.test')
    await user.type(screen.getByLabelText('Contraseña'), 'una-clave-larga')
    await user.type(screen.getByLabelText('Confirmar contraseña'), 'una-clave-larga')
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }))
    expect(await screen.findByText('Ya existe una cuenta con este correo')).toBeInTheDocument()
    expect(screen.getByLabelText('Correo electrónico')).toHaveAttribute('aria-invalid', 'true')
  })
})
