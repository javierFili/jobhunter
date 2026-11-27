import { useEffect } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { FormField } from '@/components/FormField'
import { SelectField } from '@/components/SelectField'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { COUNTRY_CODES, countryName } from '@/lib/labels'
import type { ProfileDto, ProfileRequest } from '@/lib/types'
import { blankToNull, handleFormError, optionalEmail, optionalHttpUrl, optionalText, requiredText } from '@/lib/zod'
import { profilesApi } from '../api'
import { FormDialog } from '../components/FormDialog'

const schema = z.object({
  name: requiredText(100, 'Ingresa un nombre para el perfil'),
  headline: optionalText(200),
  summary: optionalText(3000),
  targetRole: optionalText(150),
  isDefault: z.boolean(),
  contactEmail: optionalEmail,
  phone: optionalText(40),
  city: optionalText(100),
  country: z.string(),
  githubUrl: optionalHttpUrl,
  linkedinUrl: optionalHttpUrl,
  portfolioUrl: optionalHttpUrl,
  websiteUrl: optionalHttpUrl,
})
type Values = z.infer<typeof schema>

const countryOptions = COUNTRY_CODES.map((c) => ({ value: c as string, label: countryName(c) }))

const toValues = (p: ProfileDto | null, isFirst: boolean): Values => ({
  name: p?.name ?? '',
  headline: p?.headline ?? '',
  summary: p?.summary ?? '',
  targetRole: p?.targetRole ?? '',
  isDefault: p?.isDefault ?? isFirst,
  contactEmail: p?.contactEmail ?? '',
  phone: p?.phone ?? '',
  city: p?.city ?? '',
  country: p?.country ?? '',
  githubUrl: p?.githubUrl ?? '',
  linkedinUrl: p?.linkedinUrl ?? '',
  portfolioUrl: p?.portfolioUrl ?? '',
  websiteUrl: p?.websiteUrl ?? '',
})

export function ProfileForm({
  open,
  onOpenChange,
  profile,
  isFirst,
}: {
  open: boolean
  onOpenChange: (o: boolean) => void
  profile: ProfileDto | null
  isFirst: boolean
}) {
  const create = profilesApi.useCreate()
  const update = profilesApi.useUpdate()
  const {
    register,
    control,
    handleSubmit,
    setError,
    reset,
    formState: { errors },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: toValues(profile, isFirst) })

  useEffect(() => {
    if (open) reset(toValues(profile, isFirst))
  }, [open, profile, isFirst, reset])

  const onSubmit = handleSubmit(async (v) => {
    const body: ProfileRequest = {
      name: v.name.trim(),
      headline: blankToNull(v.headline),
      summary: blankToNull(v.summary),
      targetRole: blankToNull(v.targetRole),
      isDefault: v.isDefault,
      contactEmail: blankToNull(v.contactEmail),
      phone: blankToNull(v.phone),
      city: blankToNull(v.city),
      country: blankToNull(v.country),
      githubUrl: blankToNull(v.githubUrl),
      linkedinUrl: blankToNull(v.linkedinUrl),
      portfolioUrl: blankToNull(v.portfolioUrl),
      websiteUrl: blankToNull(v.websiteUrl),
    }
    try {
      if (profile) {
        await update.mutateAsync({ id: profile.id, body: { ...body, version: profile.version } })
        toast.success('Perfil actualizado')
      } else {
        await create.mutateAsync(body)
        toast.success('Perfil creado')
      }
      onOpenChange(false)
    } catch (e) {
      handleFormError(e, setError)
    }
  })

  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title={profile ? 'Editar perfil profesional' : 'Nuevo perfil profesional'}
      description="Un perfil es una versión de tu presentación orientada a un tipo de puesto (ej. «Backend Java», «Full stack React»)."
      onSubmit={onSubmit}
      submitting={create.isPending || update.isPending}
      wide
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="pf-name" label="Nombre del perfil" required error={errors.name?.message}>
          <Input {...register('name')} />
        </FormField>
        <FormField id="pf-target" label="Puesto objetivo" error={errors.targetRole?.message}>
          <Input placeholder="Ej.: Backend Developer" {...register('targetRole')} />
        </FormField>
      </div>
      <FormField id="pf-headline" label="Titular" hint="Una línea que te describe profesionalmente." error={errors.headline?.message}>
        <Input {...register('headline')} />
      </FormField>
      <FormField id="pf-summary" label="Resumen" error={errors.summary?.message}>
        <Textarea rows={4} {...register('summary')} />
      </FormField>
      <fieldset className="grid gap-4 rounded-lg border p-4 sm:grid-cols-2">
        <legend className="px-1 text-sm font-medium">Contacto</legend>
        <p className="text-xs text-muted-foreground sm:col-span-2">
          Tus datos de contacto nunca se envían a proveedores de IA.
        </p>
        <FormField id="pf-email" label="Correo de contacto" error={errors.contactEmail?.message}>
          <Input type="email" {...register('contactEmail')} />
        </FormField>
        <FormField id="pf-phone" label="Teléfono" error={errors.phone?.message}>
          <Input type="tel" {...register('phone')} />
        </FormField>
        <FormField id="pf-city" label="Ciudad" error={errors.city?.message}>
          <Input {...register('city')} />
        </FormField>
        <FormField id="pf-country" label="País" error={errors.country?.message}>
          <Controller
            control={control}
            name="country"
            render={({ field }) => (
              <SelectField value={field.value} onChange={field.onChange} options={countryOptions} noneLabel="Sin especificar" />
            )}
          />
        </FormField>
      </fieldset>
      <fieldset className="grid gap-4 rounded-lg border p-4 sm:grid-cols-2">
        <legend className="px-1 text-sm font-medium">Enlaces</legend>
        <FormField id="pf-github" label="GitHub" error={errors.githubUrl?.message}>
          <Input type="url" placeholder="https://github.com/…" {...register('githubUrl')} />
        </FormField>
        <FormField id="pf-linkedin" label="LinkedIn" error={errors.linkedinUrl?.message}>
          <Input type="url" placeholder="https://linkedin.com/in/…" {...register('linkedinUrl')} />
        </FormField>
        <FormField id="pf-portfolio" label="Portafolio" error={errors.portfolioUrl?.message}>
          <Input type="url" placeholder="https://" {...register('portfolioUrl')} />
        </FormField>
        <FormField id="pf-website" label="Sitio web" error={errors.websiteUrl?.message}>
          <Input type="url" placeholder="https://" {...register('websiteUrl')} />
        </FormField>
      </fieldset>
      <div className="flex items-center gap-3">
        <Controller
          control={control}
          name="isDefault"
          render={({ field }) => <Switch id="pf-default" checked={field.value} onCheckedChange={field.onChange} />}
        />
        <Label htmlFor="pf-default" className="font-normal">
          Usar como perfil predeterminado
        </Label>
      </div>
    </FormDialog>
  )
}
