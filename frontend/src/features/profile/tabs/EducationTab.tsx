import { useEffect } from 'react'
import { Controller, useForm, type Resolver } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { GraduationCap } from 'lucide-react'
import { toast } from 'sonner'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { DataTable } from '@/components/DataTable'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { FormField } from '@/components/FormField'
import { LoadingState } from '@/components/LoadingState'
import { SelectField } from '@/components/SelectField'
import { StatusBadge, VerifiedBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { formatPeriod } from '@/lib/format'
import { educationStatusLabels, toOptions } from '@/lib/labels'
import { errorMessage } from '@/lib/problem'
import type { EducationDto } from '@/lib/types'
import { handleFormError } from '@/lib/zod'
import { educationsApi } from '../api'
import { FormDialog } from '../components/FormDialog'
import { educationSchema, educationToRequest, type EducationValues } from '../schemas'
import { RowActions } from '../components/RowActions'
import { TabToolbar } from '../components/TabToolbar'
import { useEntityDialogs } from '../components/useEntityDialogs'
import { useSelection } from '../components/useSelection'
import { useVerifyAction } from '../components/useVerifyAction'

type Values = EducationValues
const statusOptions = toOptions(educationStatusLabels)
const statusTone = { COMPLETED: 'success', IN_PROGRESS: 'info', INCOMPLETE: 'neutral' } as const

const toValues = (e: EducationDto | null): Values => ({
  institution: e?.institution ?? '',
  degree: e?.degree ?? '',
  fieldOfStudy: e?.fieldOfStudy ?? '',
  startDate: e?.startDate ?? '',
  endDate: e?.endDate ?? '',
  status: e?.status ?? 'COMPLETED',
  description: e?.description ?? '',
})

function EducationForm({ open, onOpenChange, education }: { open: boolean; onOpenChange: (o: boolean) => void; education: EducationDto | null }) {
  const create = educationsApi.useCreate()
  const update = educationsApi.useUpdate()
  const {
    register,
    control,
    handleSubmit,
    setError,
    reset,
    formState: { errors },
  } = useForm<Values>({ resolver: zodResolver(educationSchema) as unknown as Resolver<Values>, defaultValues: toValues(education) })

  useEffect(() => {
    if (open) reset(toValues(education))
  }, [open, education, reset])

  const onSubmit = handleSubmit(async (v) => {
    const body = educationToRequest(v as z.infer<typeof educationSchema>)
    try {
      if (education) {
        await update.mutateAsync({ id: education.id, body: { ...body, version: education.version } })
        toast.success('Formación actualizada')
      } else {
        await create.mutateAsync(body)
        toast.success('Formación agregada')
      }
      onOpenChange(false)
    } catch (e) {
      handleFormError(e, setError, { dateRangeValid: 'endDate' })
    }
  })

  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title={education ? 'Editar formación' : 'Agregar formación'}
      onSubmit={onSubmit}
      submitting={create.isPending || update.isPending}
    >
      <FormField id="edu-institution" label="Institución" required error={errors.institution?.message}>
        <Input {...register('institution')} />
      </FormField>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="edu-degree" label="Título" hint="Ej.: Licenciatura, Técnico superior." error={errors.degree?.message}>
          <Input {...register('degree')} />
        </FormField>
        <FormField id="edu-field" label="Área de estudio" error={errors.fieldOfStudy?.message}>
          <Input {...register('fieldOfStudy')} />
        </FormField>
        <FormField id="edu-start" label="Fecha de inicio" error={errors.startDate?.message}>
          <Input type="date" {...register('startDate')} />
        </FormField>
        <FormField id="edu-end" label="Fecha de fin" error={errors.endDate?.message}>
          <Input type="date" {...register('endDate')} />
        </FormField>
        <FormField id="edu-status" label="Estado" error={errors.status?.message}>
          <Controller
            control={control}
            name="status"
            render={({ field }) => (
              <SelectField value={field.value} onChange={(v) => field.onChange(v || 'COMPLETED')} options={statusOptions} />
            )}
          />
        </FormField>
      </div>
      <FormField id="edu-description" label="Descripción" error={errors.description?.message}>
        <Textarea rows={3} {...register('description')} />
      </FormField>
    </FormDialog>
  )
}

export function EducationTab() {
  const { data, isLoading, error, refetch } = educationsApi.useList()
  const remove = educationsApi.useDelete()
  const dialogs = useEntityDialogs<EducationDto>()
  const selection = useSelection<number>()
  const verify = useVerifyAction('educationIds', selection.clear)

  return (
    <section aria-labelledby="tab-education-title">
      <h2 id="tab-education-title" className="sr-only">
        Educación
      </h2>
      <TabToolbar
        description="Estudios universitarios, técnicos y otros programas formales."
        addLabel="Agregar formación"
        onAdd={dialogs.openCreate}
        selectedCount={selection.selected.length}
        onVerify={() => verify.run(selection.selected)}
        verifying={verify.pending}
      />
      {isLoading ? (
        <LoadingState />
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <DataTable
          caption="Formación académica"
          rows={data ?? []}
          getRowId={(r) => r.id}
          getRowLabel={(r) => r.institution}
          selectable
          selected={selection.selected}
          onSelectedChange={selection.setSelected}
          empty={
            <EmptyState
              icon={GraduationCap}
              title="Aún no agregaste tu formación"
              action={<Button onClick={dialogs.openCreate}>Agregar formación</Button>}
            />
          }
          columns={[
            {
              id: 'institution',
              header: 'Institución',
              cell: (r) => (
                <div>
                  <div className="font-medium">{r.institution}</div>
                  <div className="text-xs text-muted-foreground">{[r.degree, r.fieldOfStudy].filter(Boolean).join(' · ')}</div>
                </div>
              ),
            },
            { id: 'period', header: 'Periodo', cell: (r) => formatPeriod(r.startDate, r.endDate), className: 'whitespace-nowrap' },
            { id: 'status', header: 'Estado', cell: (r) => <StatusBadge tone={statusTone[r.status]}>{educationStatusLabels[r.status]}</StatusBadge> },
            { id: 'verified', header: 'Verificación', cell: (r) => <VerifiedBadge verified={r.verified} /> },
            {
              id: 'actions',
              header: <span className="sr-only">Acciones</span>,
              cell: (r) => <RowActions name={r.institution} onEdit={() => dialogs.openEdit(r)} onDelete={() => dialogs.askDelete(r)} />,
            },
          ]}
        />
      )}
      <EducationForm open={dialogs.formOpen} onOpenChange={dialogs.setFormOpen} education={dialogs.editing} />
      <ConfirmDialog
        open={!!dialogs.deleting}
        onOpenChange={(o) => !o && dialogs.closeDelete()}
        title="¿Eliminar esta formación?"
        description={dialogs.deleting ? `Se eliminará «${dialogs.deleting.institution}».` : undefined}
        confirmLabel="Eliminar"
        destructive
        pending={remove.isPending}
        onConfirm={() =>
          dialogs.deleting &&
          remove.mutate(dialogs.deleting.id, {
            onSuccess: () => {
              toast.success('Formación eliminada')
              dialogs.closeDelete()
            },
            onError: (err) => toast.error(errorMessage(err)),
          })
        }
      />
    </section>
  )
}
