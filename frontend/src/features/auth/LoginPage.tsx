import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link } from 'react-router'
import { Loader2 } from 'lucide-react'
import { FormField } from '@/components/FormField'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { ApiError } from '@/lib/problem'
import { handleFormError } from '@/lib/zod'
import { useAuth } from './auth-context'
import { AuthLayout } from './AuthLayout'
import { loginSchema, type LoginValues } from './schemas'

export function LoginPage() {
  const { login } = useAuth()
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({ resolver: zodResolver(loginSchema), defaultValues: { email: '', password: '' } })

  const onSubmit = handleSubmit(async (values) => {
    try {
      await login(values)
    } catch (err) {
      if (err instanceof ApiError && err.code === 'INVALID_CREDENTIALS') {
        setError('root', { message: 'Correo o contraseña incorrectos.' })
      } else if (err instanceof ApiError && err.code === 'ACCOUNT_LOCKED') {
        setError('root', { message: err.problem.detail ?? 'Tu cuenta está bloqueada temporalmente.' })
      } else {
        handleFormError(err, setError)
      }
    }
  })

  return (
    <AuthLayout
      title="Iniciar sesión"
      description="Accede a tu asistente de búsqueda de empleo."
      footer={
        <>
          ¿No tienes cuenta?{' '}
          <Link to="/register" className="font-medium text-primary underline-offset-4 hover:underline">
            Crear cuenta
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate className="grid gap-4">
        {errors.root && (
          <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {errors.root.message}
          </p>
        )}
        <FormField id="email" label="Correo electrónico" error={errors.email?.message}>
          <Input type="email" autoComplete="email" {...register('email')} />
        </FormField>
        <FormField id="password" label="Contraseña" error={errors.password?.message}>
          <Input type="password" autoComplete="current-password" {...register('password')} />
        </FormField>
        <Button type="submit" size="lg" disabled={isSubmitting}>
          {isSubmitting && <Loader2 className="animate-spin" aria-hidden />}
          Iniciar sesión
        </Button>
      </form>
    </AuthLayout>
  )
}
