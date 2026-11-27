import { useEffect } from 'react'
import { Controller, useForm, type Resolver } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { FormField } from '@/components/FormField'
import { SelectField } from '@/components/SelectField'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { COUNTRY_CODES, countryName, employmentTypeLabels, toOptions, workModalityLabels } from '@/lib/labels'
import type { ExperienceDto } from '@/lib/types'
import { handleFormError } from '@/lib/zod'
import { experiencesApi } from '../api'
import { FormDialog } from '../components/FormDialog'
import { listToLines } from '../components/helpers'
import { experienceSchema, experienceToRequest, type ExperienceValues } from '../schemas'
import { SkillMultiPicker } from '../components/SkillMultiPicker'

type Values = ExperienceValues

const employmentOptions = toOptions(employmentTypeLabels)
const modalityOptions = toOptions(workModalityLabels)
const countryOptions = COUNTRY_CODES.map((c) => ({ value: c as string, label: countryName(c) }))

function toValues(e: ExperienceDto | null): Values {
  return {
    companyName: e?.companyName ?? '',
    companyUrl: e?.companyUrl ?? '',
    positionTitle: e?.positionTitle ?? '',
    employmentType: e?.employmentType ?? 'FULL_TIME',
    workModality: e?.workModality ?? 'REMOTE',
    location: e?.location ?? '',
    country: e?.country ?? '',
    startDate: e?.startDate ?? '',
    endDate: e?.endDate ?? '',
    current: e?.current ?? false,
    description: e?.description ?? '',
    achievements: listToLines(e?.achievements),
    skills: e?.skills ?? [],
  }
}

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  experience: ExperienceDto | null
}

export function ExperienceForm({ open, onOpenChange, experience }: Props) {
  const create = experiencesApi.useCreate()
  const update = experiencesApi.useUpdate()
  const form = useForm<Values>({ resolver: zodResolver(experienceSchema) as unknown as Resolver<Values>, defaultValues: toValues(experience) })
  const {
    register,
    control,
    handleSubmit,
    setError,
    reset,
    setValue,
    formState: { errors },
  } = form

  useEffect(() => {
    if (open) reset(toValues(experience))
  }, [open, experience, reset])

  const onSubmit = handleSubmit(async (v) => {
    const body = experienceToRequest(v as z.infer<typeof experienceSchema>)
    try {
      if (experience) {
        await update.mutateAsync({ id: experience.id, body: { ...body, version: experience.version } })
        toast.success('Experiencia actualizada')
      } else {
        await create.mutateAsync(body)
        toast.success('Experiencia agregada')
      }
      onOpenChange(false)
    } catch (e) {
      handleFormError(e, setError, { skillIds: 'skills', dateRangeValid: 'endDate', currentConsistent: 'endDate' })
    }
  })

  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title={experience ? 'Editar experiencia laboral' : 'Agregar experiencia laboral'}
      description="Solo empleos o contratos reales. Los proyectos personales, académicos o freelance van en «Proyectos»."
      onSubmit={onSubmit}
      submitting={create.isPending || update.isPending}
      wide
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="exp-company" label="Empresa" required error={errors.companyName?.message}>
          <Input {...register('companyName')} />
        </FormField>
        <FormField id="exp-position" label="Cargo" required error={errors.positionTitle?.message}>
          <Input {...register('positionTitle')} />
        </FormField>
        <FormField id="exp-type" label="Tipo de empleo" error={errors.employmentType?.message}>
          <Controller
            control={control}
            name="employmentType"
            render={({ field }) => (
              <SelectField value={field.value} onChange={(v) => field.onChange(v || 'FULL_TIME')} options={employmentOptions} />
            )}
          />
        </FormField>
        <FormField id="exp-modality" label="Modalidad" error={errors.workModality?.message}>
          <Controller
            control={control}
            name="workModality"
            render={({ field }) => (
              <SelectField value={field.value} onChange={(v) => field.onChange(v || 'REMOTE')} options={modalityOptions} />
            )}
          />
        </FormField>
        <FormField id="exp-location" label="Ciudad / ubicación" error={errors.location?.message}>
          <Input {...register('location')} />
        </FormField>
        <FormField id="exp-country" label="País" error={errors.country?.message}>
          <Controller
            control={control}
            name="country"
            render={({ field }) => (
              <SelectField value={field.value} onChange={field.onChange} options={countryOptions} noneLabel="Sin especificar" />
            )}
          />
        </FormField>
        <FormField id="exp-url" label="Sitio web de la empresa" error={errors.companyUrl?.message} className="sm:col-span-2">
          <Input type="url" placeholder="https://" {...register('companyUrl')} />
        </FormField>
        <FormField id="exp-start" label="Fecha de inicio" required error={errors.startDate?.message}>
          <Input type="date" {...register('startDate')} />
        </FormField>
        <Controller
          control={control}
          name="current"
          render={({ field: currentField }) => (
            <div className="grid gap-2">
              <FormField id="exp-end" label="Fecha de fin" error={errors.endDate?.message}>
                <Input type="date" disabled={currentField.value} {...register('endDate')} />
              </FormField>
              <div className="flex items-center gap-2">
                <Checkbox
                  id="exp-current"
                  checked={currentField.value}
                  onCheckedChange={(c) => {
                    currentField.onChange(c === true)
                    if (c === true) setValue('endDate', '')
                  }}
                />
                <Label htmlFor="exp-current" className="font-normal">
                  Trabajo aquí actualmente
                </Label>
              </div>
            </div>
          )}
        />
      </div>
      <FormField id="exp-description" label="Descripción" error={errors.description?.message}>
        <Textarea rows={3} {...register('description')} />
      </FormField>
      <FormField id="exp-achievements" label="Logros" hint="Uno por línea." error={errors.achievements?.message}>
        <Textarea rows={3} {...register('achievements')} />
      </FormField>
      <FormField id="exp-skills" label="Habilidades usadas" error={errors.skills?.message}>
        <Controller
          control={control}
          name="skills"
          render={({ field }) => <SkillMultiPicker value={field.value} onChange={field.onChange} />}
        />
      </FormField>
    </FormDialog>
  )
}
