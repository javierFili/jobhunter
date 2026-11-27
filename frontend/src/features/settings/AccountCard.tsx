import { useMemo } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { FormField } from '@/components/FormField'
import { SelectField } from '@/components/SelectField'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { useAuth } from '@/features/auth/auth-context'
import { useTheme } from '@/lib/theme-context'
import type { Theme, UserDto } from '@/lib/types'
import { handleFormError } from '@/lib/zod'
import { useUpdateMe } from './api'
import { themeLabels } from './useThemePreference'

const LOCALES = [
  { value: 'es-BO', label: 'Español (Bolivia)' },
  { value: 'es', label: 'Español' },
  { value: 'en', label: 'English' },
  { value: 'pt-BR', label: 'Português (Brasil)' },
]

const COMMON_TZ = [
  'America/La_Paz',
  'America/Argentina/Buenos_Aires',
  'America/Santiago',
  'America/Lima',
  'America/Bogota',
  'America/Mexico_City',
  'America/Sao_Paulo',
  'America/New_York',
  'Europe/Madrid',
  'Europe/Rome',
  'Europe/Berlin',
  'UTC',
]

const schema = z.object({
  fullName: z.string().trim().min(2, 'Ingresa tu nombre completo').max(120, 'Máximo 120 caracteres'),
  locale: z.string().min(1, 'Elige un idioma'),
  timezone: z.string().min(1, 'Elige una zona horaria'),
  theme: z.enum(['light', 'dark', 'system']),
})
type Values = z.infer<typeof schema>

function AccountForm({ user }: { user: UserDto }) {
  const update = useUpdateMe()
  const { setTheme } = useTheme()
  const tzOptions = useMemo(() => {
    let all: string[] = COMMON_TZ
    try {
      all = Array.from(new Set([...COMMON_TZ, ...Intl.supportedValuesOf('timeZone')]))
    } catch {
      // older runtimes
    }
    if (user.timezone && !all.includes(user.timezone)) all = [user.timezone, ...all]
    return all.map((tz) => ({ value: tz, label: tz.replace(/_/g, ' ') }))
  }, [user.timezone])
  const localeOptions = LOCALES.some((l) => l.value === user.locale) || !user.locale ? LOCALES : [{ value: user.locale, label: user.locale }, ...LOCALES]

  const {
    register,
    control,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isDirty },
  } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { fullName: user.fullName, locale: user.locale, timezone: user.timezone, theme: user.theme },
  })

  const onSubmit = handleSubmit(async (v) => {
    try {
      const saved = await update.mutateAsync({ ...v, theme: v.theme as Theme })
      setTheme(saved.theme)
      reset({ fullName: saved.fullName, locale: saved.locale, timezone: saved.timezone, theme: saved.theme })
      toast.success('Cuenta actualizada')
    } catch (e) {
      handleFormError(e, setError)
    }
  })

  return (
    <form onSubmit={onSubmit} noValidate>
      <CardContent className="grid gap-4 sm:grid-cols-2">
        <FormField id="acc-name" label="Nombre completo" error={errors.fullName?.message}>
          <Input autoComplete="name" {...register('fullName')} />
        </FormField>
        <FormField id="acc-email" label="Correo electrónico" hint="El correo no se puede cambiar.">
          <Input value={user.email} readOnly disabled />
        </FormField>
        <FormField id="acc-locale" label="Idioma" error={errors.locale?.message}>
          <Controller control={control} name="locale" render={({ field }) => <SelectField value={field.value} onChange={field.onChange} options={localeOptions} />} />
        </FormField>
        <FormField id="acc-tz" label="Zona horaria" error={errors.timezone?.message}>
          <Controller control={control} name="timezone" render={({ field }) => <SelectField value={field.value} onChange={field.onChange} options={tzOptions} />} />
        </FormField>
        <fieldset className="grid gap-2 sm:col-span-2">
          <legend className="mb-1 text-sm font-medium">Tema</legend>
          <Controller
            control={control}
            name="theme"
            render={({ field }) => (
              <RadioGroup value={field.value} onValueChange={field.onChange} className="flex flex-wrap gap-4">
                {(['light', 'dark', 'system'] as const).map((t) => (
                  <div key={t} className="flex items-center gap-2">
                    <RadioGroupItem id={`acc-theme-${t}`} value={t} />
                    <Label htmlFor={`acc-theme-${t}`} className="font-normal">
                      {themeLabels[t]}
                    </Label>
                  </div>
                ))}
              </RadioGroup>
            )}
          />
        </fieldset>
      </CardContent>
      <CardFooter className="mt-4 justify-end border-t pt-4">
        <Button type="submit" disabled={!isDirty || update.isPending}>
          {update.isPending && <Loader2 className="animate-spin" aria-hidden />}
          Guardar cambios
        </Button>
      </CardFooter>
    </form>
  )
}

export function AccountCard() {
  const { user } = useAuth()
  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h2 className="text-base font-semibold">Cuenta</h2>
        </CardTitle>
        <CardDescription>Tus datos básicos y preferencias de visualización.</CardDescription>
      </CardHeader>
      {user && <AccountForm user={user} />}
    </Card>
  )
}
