import { screen, waitFor } from '@testing-library/react'
import { http } from 'msw'
import { describe, expect, it } from 'vitest'
import { API, problem } from '@/test/handlers'
import { renderApp } from '@/test/render'
import { server } from '@/test/server'

const anonymous = () => server.use(http.post(`${API}/auth/refresh`, () => problem(401, 'UNAUTHORIZED', 'Sin sesión')))

describe('login', () => {
  it('redirects to /login when the session cannot be restored', async () => {
    anonymous()
    renderApp('/profile')
    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/login')
  })

  it('validates required fields and email format', async () => {
    anonymous()
    const { user } = renderApp('/login')
    await user.click(await screen.findByRole('button', { name: 'Iniciar sesión' }))
    expect(await screen.findByText('Ingresa tu correo electrónico')).toBeInTheDocument()
    expect(screen.getByText('Ingresa tu contraseña')).toBeInTheDocument()
    expect(screen.getByLabelText('Correo electrónico')).toHaveAttribute('aria-invalid', 'true')

    await user.type(screen.getByLabelText('Correo electrónico'), 'no-es-un-correo')
    await user.click(screen.getByRole('button', { name: 'Iniciar sesión' }))
    expect(await screen.findByText('Correo electrónico inválido')).toBeInTheDocument()
  })

  it('shows an error for invalid credentials', async () => {
    anonymous()
    const { user } = renderApp('/login')
    await user.type(await screen.findByLabelText('Correo electrónico'), 'test.user@example.test')
    await user.type(screen.getByLabelText('Contraseña'), 'wrong-password')
    await user.click(screen.getByRole('button', { name: 'Iniciar sesión' }))
    expect(await screen.findByText('Correo o contraseña incorrectos.')).toBeInTheDocument()
  })

  it('logs in and lands on the dashboard', async () => {
    anonymous()
    const { user } = renderApp('/login')
    await user.type(await screen.findByLabelText('Correo electrónico'), 'test.user@example.test')
    await user.type(screen.getByLabelText('Contraseña'), 'correct-password')
    await user.click(screen.getByRole('button', { name: 'Iniciar sesión' }))
    expect(await screen.findByRole('heading', { name: /Hola, TEST/ })).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/)
  })
})

describe('register', () => {
  it('requires a password of at least 10 characters and matching confirmation', async () => {
    anonymous()
    const { user } = renderApp('/register')
    await user.type(await screen.findByLabelText('Nombre completo'), 'TEST DATA Persona')
    await user.type(screen.getByLabelText('Correo electrónico'), 'test.new@example.test')
    await user.type(screen.getByLabelText('Contraseña'), 'corta123')
    await user.type(screen.getByLabelText('Confirmar contraseña'), 'distinta123')
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }))
    expect(await screen.findByText('La contraseña debe tener al menos 10 caracteres')).toBeInTheDocument()
    expect(screen.getByText('Las contraseñas no coinciden')).toBeInTheDocument()
  })

  it('maps server field errors (problem.errors[]) onto the form', async () => {
    anonymous()
    server.use(
      http.post(`${API}/auth/register`, () =>
        problem(409, 'EMAIL_TAKEN', 'El correo ya está registrado', [{ field: 'email', message: 'Este correo ya está en uso' }]),
      ),
    )
    const { user } = renderApp('/register')
    await user.type(await screen.findByLabelText('Nombre completo'), 'TEST DATA Persona')
    await user.type(screen.getByLabelText('Correo electrónico'), 'test.taken@example.test')
    await user.type(screen.getByLabelText('Contraseña'), 'una-clave-larga')
    await user.type(screen.getByLabelText('Confirmar contraseña'), 'una-clave-larga')
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }))
    expect(await screen.findByText('Este correo ya está en uso')).toBeInTheDocument()
    // detail shown as toast
    expect(await screen.findByText('El correo ya está registrado')).toBeInTheDocument()
  })

  it('registers and enters the app', async () => {
    anonymous()
    const { user } = renderApp('/register')
    await user.type(await screen.findByLabelText('Nombre completo'), 'TEST DATA Nueva Persona')
    await user.type(screen.getByLabelText('Correo electrónico'), 'test.new@example.test')
    await user.type(screen.getByLabelText('Contraseña'), 'una-clave-larga')
    await user.type(screen.getByLabelText('Confirmar contraseña'), 'una-clave-larga')
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/))
  })
})
