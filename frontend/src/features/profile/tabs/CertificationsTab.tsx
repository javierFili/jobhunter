import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Award, ExternalLink } from 'lucide-react'
import { toast } from 'sonner'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { DataTable } from '@/components/DataTable'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { FormField } from '@/components/FormField'
import { LoadingState } from '@/components/LoadingState'
import { StatusBadge, VerifiedBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { formatDate } from '@/lib/format'
import { errorMessage } from '@/lib/problem'
import type { CertificationDto } from '@/lib/types'
import { handleFormError } from '@/lib/zod'
import { certificationsApi } from '../api'
import { FormDialog } from '../components/FormDialog'
import { certificationSchema, certificationToRequest, type CertificationValues } from '../schemas'
import { RowActions } from '../components/RowActions'
import { TabToolbar } from '../components/TabToolbar'
import { useEntityDialogs } from '../components/useEntityDialogs'
import { useSelection } from '../components/useSelection'
import { useVerifyAction } from '../components/useVerifyAction'

type Values = CertificationValues

const toValues = (c: CertificationDto | null): Values => ({
  name: c?.name ?? '',
  issuer: c?.issuer ?? '',
  issuedAt: c?.issuedAt ?? '',
  expiresAt: c?.expiresAt ?? '',
  credentialId: c?.credentialId ?? '',
  credentialUrl: c?.credentialUrl ?? '',
})

const isExpired = (c: CertificationDto) => !!c.expiresAt && c.expiresAt < new Date().toISOString().slice(0, 10)

function CertificationForm({ open, onOpenChange, certification }: { open: boolean; onOpenChange: (o: boolean) => void; certification: CertificationDto | null }) {
  const create = certificationsApi.useCreate()
  const update = certificationsApi.useUpdate()
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors },
  } = useForm<Values>({ resolver: zodResolver(certificationSchema), defaultValues: toValues(certification) })

  useEffect(() => {
    if (open) reset(toValues(certification))
  }, [open, certification, reset])

  const onSubmit = handleSubmit(async (v) => {
    const body = certificationToRequest(v)
    try {
      if (certification) {
        await update.mutateAsync({ id: certification.id, body: { ...body, version: certification.version } })
        toast.success('Certificación actualizada')
      } else {
        await create.mutateAsync(body)
        toast.success('Certificación agregada')
      }
      onOpenChange(false)
    } catch (e) {
      handleFormError(e, setError, { dateRangeValid: 'expiresAt' })
    }
  })

  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title={certification ? 'Editar certificación' : 'Agregar certificación'}
      onSubmit={onSubmit}
      submitting={create.isPending || update.isPending}
    >
      <FormField id="cert-name" label="Nombre" required error={errors.name?.message}>
        <Input {...register('name')} />
      </FormField>
      <FormField id="cert-issuer" label="Entidad emisora" error={errors.issuer?.message}>
        <Input {...register('issuer')} />
      </FormField>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="cert-issued" label="Fecha de emisión" error={errors.issuedAt?.message}>
          <Input type="date" {...register('issuedAt')} />
        </FormField>
        <FormField id="cert-expires" label="Fecha de vencimiento" error={errors.expiresAt?.message}>
          <Input type="date" {...register('expiresAt')} />
        </FormField>
        <FormField id="cert-credential" label="ID de credencial" error={errors.credentialId?.message}>
          <Input {...register('credentialId')} />
        </FormField>
        <FormField id="cert-url" label="URL de verificación" error={errors.credentialUrl?.message}>
          <Input type="url" placeholder="https://" {...register('credentialUrl')} />
        </FormField>
      </div>
    </FormDialog>
  )
}

export function CertificationsTab() {
  const { data, isLoading, error, refetch } = certificationsApi.useList()
  const remove = certificationsApi.useDelete()
  const dialogs = useEntityDialogs<CertificationDto>()
  const selection = useSelection<number>()
  const verify = useVerifyAction('certificationIds', selection.clear)

  return (
    <section aria-labelledby="tab-cert-title">
      <h2 id="tab-cert-title" className="sr-only">
        Certificaciones
      </h2>
      <TabToolbar
        description="Certificaciones profesionales y cursos con credencial verificable."
        addLabel="Agregar certificación"
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
          caption="Certificaciones"
          rows={data ?? []}
          getRowId={(r) => r.id}
          getRowLabel={(r) => r.name}
          selectable
          selected={selection.selected}
          onSelectedChange={selection.setSelected}
          empty={
            <EmptyState
              icon={Award}
              title="Aún no agregaste certificaciones"
              action={<Button onClick={dialogs.openCreate}>Agregar certificación</Button>}
            />
          }
          columns={[
            {
              id: 'name',
              header: 'Certificación',
              cell: (r) => (
                <div>
                  <div className="flex items-center gap-1.5 font-medium">
                    {r.name}
                    {r.credentialUrl && (
                      <a href={r.credentialUrl} target="_blank" rel="noreferrer" aria-label={`Ver credencial de ${r.name}`} className="text-muted-foreground hover:text-foreground">
                        <ExternalLink className="size-3.5" aria-hidden />
                      </a>
                    )}
                  </div>
                  <div className="text-xs text-muted-foreground">{r.issuer}</div>
                </div>
              ),
            },
            { id: 'issued', header: 'Emisión', cell: (r) => formatDate(r.issuedAt), className: 'whitespace-nowrap' },
            {
              id: 'expires',
              header: 'Vencimiento',
              className: 'whitespace-nowrap',
              cell: (r) =>
                isExpired(r) ? <StatusBadge tone="danger">Vencida · {formatDate(r.expiresAt)}</StatusBadge> : r.expiresAt ? formatDate(r.expiresAt) : 'Sin vencimiento',
            },
            { id: 'verified', header: 'Verificación', cell: (r) => <VerifiedBadge verified={r.verified} /> },
            {
              id: 'actions',
              header: <span className="sr-only">Acciones</span>,
              cell: (r) => <RowActions name={r.name} onEdit={() => dialogs.openEdit(r)} onDelete={() => dialogs.askDelete(r)} />,
            },
          ]}
        />
      )}
      <CertificationForm open={dialogs.formOpen} onOpenChange={dialogs.setFormOpen} certification={dialogs.editing} />
      <ConfirmDialog
        open={!!dialogs.deleting}
        onOpenChange={(o) => !o && dialogs.closeDelete()}
        title="¿Eliminar esta certificación?"
        description={dialogs.deleting ? `Se eliminará «${dialogs.deleting.name}».` : undefined}
        confirmLabel="Eliminar"
        destructive
        pending={remove.isPending}
        onConfirm={() =>
          dialogs.deleting &&
          remove.mutate(dialogs.deleting.id, {
            onSuccess: () => {
              toast.success('Certificación eliminada')
              dialogs.closeDelete()
            },
            onError: (err) => toast.error(errorMessage(err)),
          })
        }
      />
    </section>
  )
}
