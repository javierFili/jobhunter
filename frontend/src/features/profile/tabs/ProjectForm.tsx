import { useEffect } from 'react'
import { Controller, useForm, type Resolver } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { FormField } from '@/components/FormField'
import { SelectField } from '@/components/SelectField'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { projectKindLabels, toOptions } from '@/lib/labels'
import type { ProjectDto } from '@/lib/types'
import { handleFormError } from '@/lib/zod'
import { projectsApi } from '../api'
import { FormDialog } from '../components/FormDialog'
import { listToLines } from '../components/helpers'
import { projectSchema, projectToRequest, type ProjectValues } from '../schemas'
import { SkillMultiPicker } from '../components/SkillMultiPicker'

type Values = ProjectValues
const kindOptions = toOptions(projectKindLabels)

function toValues(p: ProjectDto | null): Values {
  return {
    kind: p?.kind ?? 'PERSONAL',
    name: p?.name ?? '',
    role: p?.role ?? '',
    description: p?.description ?? '',
    url: p?.url ?? '',
    repoUrl: p?.repoUrl ?? '',
    startDate: p?.startDate ?? '',
    endDate: p?.endDate ?? '',
    highlights: listToLines(p?.highlights),
    skills: p?.skills ?? [],
  }
}

export function ProjectForm({
  open,
  onOpenChange,
  project,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  project: ProjectDto | null
}) {
  const create = projectsApi.useCreate()
  const update = projectsApi.useUpdate()
  const {
    register,
    control,
    handleSubmit,
    setError,
    reset,
    formState: { errors },
  } = useForm<Values>({ resolver: zodResolver(projectSchema) as unknown as Resolver<Values>, defaultValues: toValues(project) })

  useEffect(() => {
    if (open) reset(toValues(project))
  }, [open, project, reset])

  const onSubmit = handleSubmit(async (v) => {
    const body = projectToRequest(v as z.infer<typeof projectSchema>)
    try {
      if (project) {
        await update.mutateAsync({ id: project.id, body: { ...body, version: project.version } })
        toast.success('Proyecto actualizado')
      } else {
        await create.mutateAsync(body)
        toast.success('Proyecto agregado')
      }
      onOpenChange(false)
    } catch (e) {
      handleFormError(e, setError, { skillIds: 'skills', dateRangeValid: 'endDate' })
    }
  })

  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title={project ? 'Editar proyecto' : 'Agregar proyecto'}
      description="Los proyectos nunca se presentan como empleo. Indica claramente de qué tipo es."
      onSubmit={onSubmit}
      submitting={create.isPending || update.isPending}
      wide
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="prj-kind" label="Tipo de proyecto" required error={errors.kind?.message}>
          <Controller
            control={control}
            name="kind"
            render={({ field }) => (
              <SelectField value={field.value} onChange={(v) => field.onChange(v || 'PERSONAL')} options={kindOptions} />
            )}
          />
        </FormField>
        <FormField id="prj-name" label="Nombre" required error={errors.name?.message}>
          <Input {...register('name')} />
        </FormField>
        <FormField id="prj-role" label="Tu rol" hint="Ej.: Desarrollador full stack, mantenedor." error={errors.role?.message}>
          <Input {...register('role')} />
        </FormField>
        <div className="hidden sm:block" />
        <FormField id="prj-url" label="URL del proyecto" error={errors.url?.message}>
          <Input type="url" placeholder="https://" {...register('url')} />
        </FormField>
        <FormField id="prj-repo" label="Repositorio" error={errors.repoUrl?.message}>
          <Input type="url" placeholder="https://github.com/…" {...register('repoUrl')} />
        </FormField>
        <FormField id="prj-start" label="Fecha de inicio" error={errors.startDate?.message}>
          <Input type="date" {...register('startDate')} />
        </FormField>
        <FormField id="prj-end" label="Fecha de fin" error={errors.endDate?.message}>
          <Input type="date" {...register('endDate')} />
        </FormField>
      </div>
      <FormField id="prj-description" label="Descripción" error={errors.description?.message}>
        <Textarea rows={3} {...register('description')} />
      </FormField>
      <FormField id="prj-highlights" label="Aspectos destacados" hint="Uno por línea." error={errors.highlights?.message}>
        <Textarea rows={3} {...register('highlights')} />
      </FormField>
      <FormField id="prj-skills" label="Habilidades usadas" error={errors.skills?.message}>
        <Controller
          control={control}
          name="skills"
          render={({ field }) => <SkillMultiPicker value={field.value} onChange={field.onChange} />}
        />
      </FormField>
    </FormDialog>
  )
}
