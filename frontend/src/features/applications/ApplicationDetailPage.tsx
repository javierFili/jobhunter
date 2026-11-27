import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ArrowLeft, ClipboardList, ExternalLink as ExternalIcon, Loader2, Save, Send, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { ErrorState } from '@/components/ErrorState'
import { ExternalLink } from '@/components/ExternalLink'
import { FormField } from '@/components/FormField'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { SelectField } from '@/components/SelectField'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { useCoverLetters } from '@/features/coverletters/api'
import { formatLabels } from '@/features/coverletters/labels'
import { MatchScoreChip } from '@/features/matching/MatchScoreChip'
import { useResumes, useVersions } from '@/features/resumes/api'
import { formatDateTime } from '@/lib/format'
import { CURRENCIES, salaryPeriodLabels, toOptions } from '@/lib/labels'
import { ApiError, errorMessage } from '@/lib/problem'
import type { SalaryPeriod } from '@/lib/types'
import { useAddEvent, useApplication, useDeleteApplication, useEvents, usePatchApplication } from './api'
import { PRE_APPLIED, eventLabels, isOverdue, methodLabels, statusLabels, statusTone } from './labels'
import { MarkSubmittedDialog } from './MarkSubmittedDialog'
import { MoveMenu } from './MoveMenu'
import type { AppMethod, ApplicationDto } from './types'

const URL_RE = /^https?:\/\/\S+$/i

function DetailsForm({ app }: { app: ApplicationDto }) {
  const patch = usePatchApplication(app.id)
  const resumes = useResumes()
  const letters = useCoverLetters({ jobId: app.jobOfferId, size: 50 })
  const [f, setF] = useState({
    companyName: app.companyName,
    positionTitle: app.positionTitle,
    jobUrl: app.jobUrl ?? '',
    method: (app.method ?? '') as AppMethod | '',
    resumeId: app.resumeId != null ? String(app.resumeId) : '',
    resumeVersionNo: app.resumeVersionNo != null ? String(app.resumeVersionNo) : '',
    coverLetterId: app.coverLetterId != null ? String(app.coverLetterId) : '',
    salaryExpectation: app.salaryExpectation != null ? String(app.salaryExpectation) : '',
    salaryCurrency: app.salaryCurrency ?? '',
    salaryPeriod: (app.salaryPeriod ?? '') as SalaryPeriod | '',
    followUpAt: app.followUpAt ?? '',
    notes: app.notes ?? '',
  })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const versions = useVersions(Number(f.resumeId || -1), !!f.resumeId)
  const set = (p: Partial<typeof f>) => setF((x) => ({ ...x, ...p }))

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const errs: Record<string, string> = {}
    if (!f.companyName.trim()) errs.companyName = 'Ingresa la empresa'
    if (!f.positionTitle.trim()) errs.positionTitle = 'Ingresa el puesto'
    if (f.jobUrl && !URL_RE.test(f.jobUrl.trim())) errs.jobUrl = 'URL inválida'
    if (f.salaryExpectation && !/^\d+(\.\d{1,2})?$/.test(f.salaryExpectation)) errs.salaryExpectation = 'Monto inválido'
    if (f.resumeId && !f.resumeVersionNo) errs.resumeVersionNo = 'Elige la versión del CV'
    setErrors(errs)
    if (Object.keys(errs).length) return
    patch.mutate(
      {
        companyName: f.companyName.trim(),
        positionTitle: f.positionTitle.trim(),
        jobUrl: f.jobUrl.trim() || null,
        method: f.method || null,
        resumeId: f.resumeId ? Number(f.resumeId) : null,
        resumeVersionNo: f.resumeVersionNo ? Number(f.resumeVersionNo) : null,
        coverLetterId: f.coverLetterId ? Number(f.coverLetterId) : null,
        salaryExpectation: f.salaryExpectation ? Number(f.salaryExpectation) : null,
        salaryCurrency: f.salaryCurrency || null,
        salaryPeriod: f.salaryPeriod || null,
        followUpAt: f.followUpAt || null,
        notes: f.notes.trim() || null,
        version: app.version,
      },
      {
        onSuccess: () => toast.success('Postulación actualizada'),
        onError: (err) => toast.error(err instanceof ApiError && err.code === 'CONFLICT' ? 'Otra pestaña modificó esta postulación. Se recargaron los datos.' : errorMessage(err)),
      },
    )
  }

  return (
    <form onSubmit={submit} noValidate className="grid gap-4" aria-label="Datos de la postulación">
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="ap-company" label="Empresa" required error={errors.companyName}>
          <Input value={f.companyName} onChange={(e) => set({ companyName: e.target.value })} />
        </FormField>
        <FormField id="ap-position" label="Puesto" required error={errors.positionTitle}>
          <Input value={f.positionTitle} onChange={(e) => set({ positionTitle: e.target.value })} />
        </FormField>
        <FormField id="ap-url" label="Enlace de la oferta" error={errors.jobUrl}>
          <Input type="url" value={f.jobUrl} onChange={(e) => set({ jobUrl: e.target.value })} />
        </FormField>
        <FormField id="ap-method" label="Medio de postulación">
          <SelectField value={f.method} onChange={(m) => set({ method: m })} options={toOptions(methodLabels)} noneLabel="Sin definir" />
        </FormField>
        <FormField id="ap-resume" label="CV">
          <SelectField
            value={f.resumeId}
            onChange={(v) => set({ resumeId: v, resumeVersionNo: '' })}
            noneLabel="Sin CV"
            options={(resumes.data ?? []).map((r) => ({ value: String(r.id), label: r.title }))}
          />
        </FormField>
        <FormField id="ap-resume-version" label="Versión del CV" error={errors.resumeVersionNo}>
          <SelectField
            value={f.resumeVersionNo}
            onChange={(v) => set({ resumeVersionNo: v })}
            disabled={!f.resumeId}
            placeholder={f.resumeId ? 'Elige la versión…' : 'Primero elige un CV'}
            options={(versions.data ?? []).map((v) => ({ value: String(v.versionNo), label: `v${v.versionNo}${v.status === 'APPROVED' ? ' · aprobada' : ''}` }))}
          />
        </FormField>
        <FormField id="ap-letter" label="Carta de presentación">
          <SelectField
            value={f.coverLetterId}
            onChange={(v) => set({ coverLetterId: v })}
            noneLabel="Sin carta"
            options={(letters.data?.content ?? []).map((l) => ({ value: String(l.id), label: `${formatLabels[l.format]} · v${l.versionNo}${l.status === 'APPROVED' ? ' · aprobada' : ''}` }))}
          />
        </FormField>
        <FormField id="ap-follow" label="Próximo seguimiento" hint={isOverdue(f.followUpAt || null) ? 'La fecha de seguimiento ya pasó.' : undefined}>
          <Input type="date" value={f.followUpAt} onChange={(e) => set({ followUpAt: e.target.value })} />
        </FormField>
      </div>
      <div className="grid gap-4 sm:grid-cols-3">
        <FormField id="ap-salary" label="Pretensión salarial" error={errors.salaryExpectation}>
          <Input inputMode="decimal" value={f.salaryExpectation} onChange={(e) => set({ salaryExpectation: e.target.value })} />
        </FormField>
        <FormField id="ap-currency" label="Moneda">
          <SelectField value={f.salaryCurrency} onChange={(v) => set({ salaryCurrency: v })} noneLabel="Sin definir" options={CURRENCIES.map((c) => ({ value: c as string, label: c }))} />
        </FormField>
        <FormField id="ap-period" label="Periodo">
          <SelectField value={f.salaryPeriod} onChange={(v) => set({ salaryPeriod: v })} noneLabel="Sin definir" options={toOptions(salaryPeriodLabels)} />
        </FormField>
      </div>
      <FormField id="ap-notes" label="Notas">
        <Textarea rows={3} value={f.notes} onChange={(e) => set({ notes: e.target.value })} />
      </FormField>
      <div className="flex justify-end">
        <Button type="submit" disabled={patch.isPending}>
          {patch.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <Save aria-hidden />}
          Guardar cambios
        </Button>
      </div>
    </form>
  )
}

const eventTypeOptions = [
  { value: 'NOTE', label: 'Nota' },
  { value: 'MESSAGE_RECEIVED', label: 'Mensaje recibido' },
  { value: 'INTERVIEW_SCHEDULED', label: 'Entrevista agendada' },
  { value: 'FOLLOW_UP', label: 'Seguimiento realizado' },
] as const

function Timeline({ appId }: { appId: number }) {
  const { data, isLoading } = useEvents(appId)
  const add = useAddEvent(appId)
  const [type, setType] = useState<(typeof eventTypeOptions)[number]['value']>('NOTE')
  const [note, setNote] = useState('')
  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h2 className="text-base font-semibold">Historial</h2>
        </CardTitle>
      </CardHeader>
      <CardContent className="grid gap-4">
        <form
          className="grid gap-2"
          aria-label="Agregar al historial"
          onSubmit={(e) => {
            e.preventDefault()
            if (!note.trim()) return
            add.mutate(
              { type, note: note.trim() },
              {
                onSuccess: () => {
                  setNote('')
                  toast.success('Agregado al historial')
                },
                onError: (err) => toast.error(errorMessage(err)),
              },
            )
          }}
        >
          <FormField id="ev-type" label="Tipo">
            <SelectField value={type} onChange={(v) => v && setType(v)} options={[...eventTypeOptions]} />
          </FormField>
          <FormField id="ev-note" label="Detalle">
            <Textarea rows={2} value={note} onChange={(e) => setNote(e.target.value)} />
          </FormField>
          <div className="flex justify-end">
            <Button type="submit" size="sm" disabled={!note.trim() || add.isPending}>
              Agregar
            </Button>
          </div>
        </form>
        {isLoading ? (
          <LoadingState rows={2} />
        ) : !data?.length ? (
          <p className="text-sm text-muted-foreground">Sin eventos todavía.</p>
        ) : (
          <ol className="grid gap-3 border-l pl-4" aria-label="Eventos de la postulación">
            {data.map((ev) => (
              <li key={ev.id} className="relative grid gap-0.5 text-sm">
                <span className="absolute top-1.5 -left-[1.3rem] size-2 rounded-full bg-primary" aria-hidden />
                <span className="font-medium">
                  {eventLabels[ev.type]}
                  {ev.type === 'STATUS_CHANGE' && ev.toStatus && `: ${ev.fromStatus ? `${statusLabels[ev.fromStatus]} → ` : ''}${statusLabels[ev.toStatus]}`}
                </span>
                {ev.note && <span className="whitespace-pre-line">{ev.note}</span>}
                <span className="text-xs text-muted-foreground">{formatDateTime(ev.occurredAt)}</span>
              </li>
            ))}
          </ol>
        )}
      </CardContent>
    </Card>
  )
}

export function ApplicationDetailPage() {
  const id = Number(useParams().applicationId)
  const navigate = useNavigate()
  const { data: app, isLoading, error, refetch } = useApplication(id)
  const remove = useDeleteApplication()
  const [submitting, setSubmitting] = useState<ApplicationDto | null>(null)
  const [deleting, setDeleting] = useState(false)

  if (isLoading) return <LoadingState />
  if (error || !app) return <ErrorState error={error} onRetry={() => void refetch()} />

  return (
    <>
      <Button asChild variant="ghost" size="sm" className="mb-2 -ml-2">
        <Link to="/applications">
          <ArrowLeft aria-hidden /> Postulaciones
        </Link>
      </Button>
      <PageHeader
        title={app.positionTitle}
        description={app.companyName}
        actions={
          <>
            <StatusBadge tone={statusTone[app.status]} className="h-6 px-2.5 text-[0.8rem] font-semibold">
              {statusLabels[app.status]}
            </StatusBadge>
            {app.match && <MatchScoreChip score={app.match.scoreTotal} classification={app.match.classification} />}
          </>
        }
      />
      <div className="mb-4 flex flex-wrap gap-2">
        <Button asChild>
          <Link to={`/applications/${app.id}/prepare`}>
            <ClipboardList aria-hidden /> Preparar postulación
          </Link>
        </Button>
        {PRE_APPLIED.includes(app.status) && (
          <Button variant="outline" onClick={() => setSubmitting(app)}>
            <Send aria-hidden /> Marcar como enviada
          </Button>
        )}
        <MoveMenu application={app} onMarkSubmitted={() => setSubmitting(app)} size="sm" />
        {app.jobOfferId && (
          <Button asChild variant="outline">
            <Link to={`/jobs/${app.jobOfferId}`}>Ver oferta</Link>
          </Button>
        )}
        {app.jobUrl && (
          <Button asChild variant="outline">
            <ExternalLink href={app.jobUrl}>
              <ExternalIcon aria-hidden /> Enlace de la oferta
            </ExternalLink>
          </Button>
        )}
        <Button variant="ghost" className="text-destructive" onClick={() => setDeleting(true)}>
          <Trash2 aria-hidden /> Eliminar
        </Button>
      </div>
      {app.appliedAt && <p className="mb-4 text-sm text-muted-foreground">Enviada el {formatDateTime(app.appliedAt)}{app.method ? ` · ${methodLabels[app.method]}` : ''}</p>}
      <div className="grid gap-4 lg:grid-cols-[1fr_22rem]">
        <Card>
          <CardContent>
            <DetailsForm key={app.version} app={app} />
          </CardContent>
        </Card>
        <Timeline appId={app.id} />
      </div>
      <MarkSubmittedDialog application={submitting} onClose={() => setSubmitting(null)} />
      <ConfirmDialog
        open={deleting}
        onOpenChange={setDeleting}
        title="¿Eliminar esta postulación?"
        description="Se eliminará junto con su historial."
        confirmLabel="Eliminar"
        destructive
        pending={remove.isPending}
        onConfirm={() =>
          remove.mutate(app.id, {
            onSuccess: () => {
              toast.success('Postulación eliminada')
              navigate('/applications')
            },
            onError: (e) => toast.error(errorMessage(e)),
          })
        }
      />
    </>
  )
}
