import { useEffect } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate } from 'react-router'
import { z } from 'zod'
import { toast } from 'sonner'
import { FormField } from '@/components/FormField'
import { SelectField } from '@/components/SelectField'
import { Input } from '@/components/ui/input'
import { profilesApi } from '@/features/profile/api'
import { FormDialog } from '@/features/profile/components/FormDialog'
import { ApiError } from '@/lib/problem'
import { toOptions } from '@/lib/labels'
import { handleFormError, requiredText } from '@/lib/zod'
import { useCreateResume, useResumeTemplates } from './api'
import { resumeLanguageLabels } from './labels'
import type { ResumeLanguage, TemplateId } from './types'

const schema = z.object({
  profileId: z.string(),
  title: requiredText(120, 'Ingresa un título'),
  language: z.enum(['es', 'en', 'it', 'de', 'fr']),
  templateId: z.string().min(1, 'Elige una plantilla'),
})
type Values = z.infer<typeof schema>

const languageOptions = toOptions(resumeLanguageLabels)

export function NewResumeDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const navigate = useNavigate()
  const profiles = profilesApi.useList()
  const templates = useResumeTemplates()
  const create = useCreateResume()
  const {
    register,
    control,
    handleSubmit,
    setError,
    reset,
    formState: { errors },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { profileId: '', title: '', language: 'es', templateId: 'classic' } })

  useEffect(() => {
    if (open) reset({ profileId: '', title: '', language: 'es', templateId: 'classic' })
  }, [open, reset])

  const onSubmit = handleSubmit(async (v) => {
    try {
      const res = await create.mutateAsync({
        profileId: v.profileId ? Number(v.profileId) : null,
        title: v.title.trim(),
        language: v.language as ResumeLanguage,
        templateId: v.templateId as TemplateId,
      })
      toast.success('CV creado a partir de tu perfil verificado')
      if (res.unverifiedItemsSkipped > 0) {
        toast.warning(`${res.unverifiedItemsSkipped} elementos sin verificar no se incluyeron`, {
          description: 'Verifícalos en Mi perfil y genera una nueva versión para incluirlos.',
          duration: 10_000,
        })
      }
      onOpenChange(false)
      navigate(`/cvs/${res.resume.id}`)
    } catch (e) {
      if (e instanceof ApiError && e.code === 'PROFILE_EMPTY') {
        setError('root', {
          message: 'Tu perfil no tiene datos verificados todavía. Ve a Mi perfil y marca como verificados los elementos que quieras incluir.',
        })
        return
      }
      handleFormError(e, setError)
    }
  })

  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title="Nuevo CV"
      description="Se genera solo con los datos verificados de tu perfil."
      onSubmit={onSubmit}
      submitting={create.isPending}
      submitLabel="Crear CV"
    >
      {errors.root && (
        <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {errors.root.message}
        </p>
      )}
      <FormField id="cv-title" label="Título" required error={errors.title?.message} hint="Ej.: «CV Backend Java — inglés»">
        <Input {...register('title')} />
      </FormField>
      <FormField id="cv-profile" label="Perfil profesional" error={errors.profileId?.message}>
        <Controller
          control={control}
          name="profileId"
          render={({ field }) => (
            <SelectField
              value={field.value}
              onChange={field.onChange}
              noneLabel="Perfil predeterminado"
              options={(profiles.data ?? []).map((p) => ({ value: String(p.id), label: p.name }))}
            />
          )}
        />
      </FormField>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="cv-language" label="Idioma" error={errors.language?.message}>
          <Controller
            control={control}
            name="language"
            render={({ field }) => <SelectField value={field.value} onChange={(l) => field.onChange(l || 'es')} options={languageOptions} />}
          />
        </FormField>
        <FormField id="cv-template" label="Plantilla" error={errors.templateId?.message}>
          <Controller
            control={control}
            name="templateId"
            render={({ field }) => (
              <SelectField
                value={field.value}
                onChange={field.onChange}
                options={(templates.data ?? [{ id: 'classic', name: 'Clásica', description: '' }]).map((t) => ({ value: t.id, label: t.name }))}
              />
            )}
          />
        </FormField>
      </div>
    </FormDialog>
  )
}
