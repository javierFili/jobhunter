import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link } from 'react-router'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { FormField } from '@/components/FormField'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { ApiError } from '@/lib/problem'
import { handleFormError } from '@/lib/zod'
import { useAuth } from './auth-context'
import { AuthLayout } from './AuthLayout'
import { PASSWORD_MIN, registerSchema, type RegisterValues } from './schemas'

export function RegisterPage() {
  const { register: registerAccount } = useAuth()
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { fullName: '', email: '', password: '', confirmPassword: '' },
  })

  const onSubmit = handleSubmit(async ({ fullName, email, password }) => {
    try {
      await registerAccount({ fullName, email, password })
      toast.success('¡Cuenta creada! Bienvenido a JobHunter AI.')
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors.length === 0) {
        if (err.code === 'EMAIL_TAKEN') setError('email', { type: 'server', message: 'Ya existe una cuenta con este correo' })
        if (err.code === 'WEAK_PASSWORD') setError('password', { type: 'server', message: err.problem.detail ?? 'La contraseña es demasiado débil' })
      }
      handleFormError(err, setError)
    }
  })

  return (
    <AuthLayout
      title="Crear cuenta"
      description="Organiza tu perfil profesional y tu búsqueda de empleo."
      footer={
        <>
          ¿Ya tienes cuenta?{' '}
          <Link to="/login" className="font-medium text-primary underline-offset-4 hover:underline">
            Iniciar sesión
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate className="grid gap-4">
        <FormField id="fullName" label="Nombre completo" error={errors.fullName?.message}>
          <Input autoComplete="name" {...register('fullName')} />
        </FormField>
        <FormField id="email" label="Correo electrónico" error={errors.email?.message}>
          <Input type="email" autoComplete="email" {...register('email')} />
        </FormField>
        <FormField
          id="password"
          label="Contraseña"
          hint={`Mínimo ${PASSWORD_MIN} caracteres.`}
          error={errors.password?.message}
        >
          <Input type="password" autoComplete="new-password" {...register('password')} />
        </FormField>
        <FormField id="confirmPassword" label="Confirmar contraseña" error={errors.confirmPassword?.message}>
          <Input type="password" autoComplete="new-password" {...register('confirmPassword')} />
        </FormField>
        <Button type="submit" size="lg" disabled={isSubmitting}>
          {isSubmitting && <Loader2 className="animate-spin" aria-hidden />}
          Crear cuenta
        </Button>
      </form>
    </AuthLayout>
  )
}
