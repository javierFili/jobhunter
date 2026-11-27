import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { FormField } from '@/components/FormField'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { PASSWORD_MIN } from '@/features/auth/schemas'
import { handleFormError } from '@/lib/zod'
import { useChangePassword } from './api'

const schema = z
  .object({
    currentPassword: z.string().min(1, 'Ingresa tu contraseña actual'),
    newPassword: z.string().min(PASSWORD_MIN, `La contraseña debe tener al menos ${PASSWORD_MIN} caracteres`).max(128, 'Máximo 128 caracteres'),
    confirmPassword: z.string(),
  })
  .refine((v) => v.newPassword === v.confirmPassword, { path: ['confirmPassword'], message: 'Las contraseñas no coinciden' })
  .refine((v) => v.newPassword !== v.currentPassword, { path: ['newPassword'], message: 'La nueva contraseña debe ser diferente a la actual' })

type Values = z.infer<typeof schema>

export function PasswordCard() {
  const change = useChangePassword()
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' } })

  const onSubmit = handleSubmit(async ({ currentPassword, newPassword }) => {
    try {
      await change.mutateAsync({ currentPassword, newPassword })
      reset()
      toast.success('Contraseña actualizada')
    } catch (e) {
      handleFormError(e, setError)
    }
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h2 className="text-base font-semibold">Cambiar contraseña</h2>
        </CardTitle>
        <CardDescription>Usa al menos {PASSWORD_MIN} caracteres.</CardDescription>
      </CardHeader>
      <form onSubmit={onSubmit} noValidate>
        <CardContent className="grid gap-4 sm:grid-cols-3">
          <FormField id="pw-current" label="Contraseña actual" error={errors.currentPassword?.message}>
            <Input type="password" autoComplete="current-password" {...register('currentPassword')} />
          </FormField>
          <FormField id="pw-new" label="Nueva contraseña" error={errors.newPassword?.message}>
            <Input type="password" autoComplete="new-password" {...register('newPassword')} />
          </FormField>
          <FormField id="pw-confirm" label="Confirmar nueva contraseña" error={errors.confirmPassword?.message}>
            <Input type="password" autoComplete="new-password" {...register('confirmPassword')} />
          </FormField>
        </CardContent>
        <CardFooter className="mt-4 justify-end border-t pt-4">
          <Button type="submit" disabled={change.isPending}>
            {change.isPending && <Loader2 className="animate-spin" aria-hidden />}
            Actualizar contraseña
          </Button>
        </CardFooter>
      </form>
    </Card>
  )
}
