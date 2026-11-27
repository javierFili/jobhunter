import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { AlertTriangle, ArrowLeft, CheckCircle2, Circle, Copy, Download, ExternalLink as ExternalIcon, Send } from 'lucide-react'
import { toast } from 'sonner'
import { ErrorState } from '@/components/ErrorState'
import { ExternalLink } from '@/components/ExternalLink'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { api } from '@/lib/api'
import { copyToClipboard } from '@/lib/clipboard'
import { errorMessage } from '@/lib/problem'
import { downloadBlob } from '@/features/resumes/editor/useObjectUrl'
import { useApplication, usePrepare } from './api'
import { NEVER_SENDS, PRE_APPLIED, methodLabels, statusLabels } from './labels'
import { MarkSubmittedDialog } from './MarkSubmittedDialog'
import type { ApplicationDto } from './types'

function CopyBlock({ label, text, multiline = false }: { label: string; text: string; multiline?: boolean }) {
  return (
    <div className="grid gap-1.5">
      <div className="flex items-center justify-between gap-2">
        <span className="text-sm font-medium">{label}</span>
        <Button
          size="xs"
          variant="outline"
          aria-label={`Copiar ${label.toLowerCase()}`}
          onClick={() =>
            copyToClipboard(text).then(
              () => toast.success(`${label} copiado`),
              () => toast.error('No se pudo copiar. Selecciona el texto y cópialo manualmente.'),
            )
          }
        >
          <Copy aria-hidden /> Copiar
        </Button>
      </div>
      <div className={multiline ? 'max-h-80 overflow-auto rounded-lg border bg-muted/40 p-3 text-sm whitespace-pre-wrap' : 'rounded-lg border bg-muted/40 px-3 py-2 text-sm'}>{text}</div>
    </div>
  )
}

export function PreparePage() {
  const id = Number(useParams().applicationId)
  const app = useApplication(id)
  const { data: pkg, isLoading, error, refetch } = usePrepare(id)
  const [submitting, setSubmitting] = useState<ApplicationDto | null>(null)
  const [downloading, setDownloading] = useState(false)

  if (isLoading || app.isLoading) return <LoadingState />
  if (error || !pkg || !app.data) return <ErrorState error={error ?? app.error} onRetry={() => void refetch()} />
  const a = app.data

  const downloadCv = async () => {
    if (!pkg.resume) return
    setDownloading(true)
    try {
      const blob = await api.blob(`/resumes/${pkg.resume.resumeId}/versions/${pkg.resume.versionNo}/pdf`)
      downloadBlob(blob, `${pkg.resume.title.replace(/[^\w\- ]+/g, '').trim() || 'cv'}-v${pkg.resume.versionNo}.pdf`)
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      setDownloading(false)
    }
  }

  return (
    <>
      <Button asChild variant="ghost" size="sm" className="mb-2 -ml-2">
        <Link to={`/applications/${id}`}>
          <ArrowLeft aria-hidden /> Volver a la postulación
        </Link>
      </Button>
      <PageHeader title="Preparar postulación" description={`${a.positionTitle} · ${a.companyName}`} actions={<StatusBadge>{statusLabels[a.status]}</StatusBadge>} />
      <div className="mb-4 grid gap-3 rounded-xl border-2 border-primary/40 bg-primary/5 p-4">
        <p className="font-medium">{NEVER_SENDS}</p>
        <div className="flex flex-wrap gap-2">
          {pkg.applyUrl && (
            <Button asChild variant="outline">
              <ExternalLink href={pkg.applyUrl}>
                <ExternalIcon aria-hidden /> Abrir enlace para postular
              </ExternalLink>
            </Button>
          )}
          {PRE_APPLIED.includes(a.status) ? (
            <Button size="lg" onClick={() => setSubmitting(a)}>
              <Send aria-hidden /> Marcar como enviada
            </Button>
          ) : (
            <StatusBadge tone="success">Ya registrada como {statusLabels[a.status].toLowerCase()}</StatusBadge>
          )}
        </div>
        {pkg.method && <p className="text-sm text-muted-foreground">Medio sugerido: {methodLabels[pkg.method]}</p>}
      </div>
      {pkg.warnings.length > 0 && (
        <div role="alert" className="mb-4 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-950 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-100">
          <p className="mb-1 flex items-center gap-1.5 font-medium">
            <AlertTriangle className="size-4" aria-hidden /> Antes de enviar
          </p>
          <ul className="list-disc pl-5">
            {pkg.warnings.map((w, i) => (
              <li key={i}>{w}</li>
            ))}
          </ul>
        </div>
      )}
      <div className="grid gap-4 lg:grid-cols-[1fr_20rem]">
        <div className="grid content-start gap-4">
          <Card>
            <CardHeader>
              <CardTitle>
                <h2 className="text-base font-semibold">Correo listo para copiar</h2>
              </CardTitle>
              <CardDescription>Generado a partir de la carta y la oferta, sin IA. Revísalo antes de enviarlo desde tu correo.</CardDescription>
            </CardHeader>
            <CardContent className="grid gap-4">
              <CopyBlock label="Asunto" text={pkg.email.subject} />
              <CopyBlock label="Cuerpo" text={pkg.email.body} multiline />
            </CardContent>
          </Card>
          {pkg.coverLetter && (
            <Card>
              <CardHeader>
                <CardTitle>
                  <h2 className="text-base font-semibold">Carta de presentación</h2>
                </CardTitle>
              </CardHeader>
              <CardContent className="grid gap-4">
                {pkg.coverLetter.subject && <CopyBlock label="Asunto de la carta" text={pkg.coverLetter.subject} />}
                <CopyBlock label="Texto de la carta" text={pkg.coverLetter.body} multiline />
              </CardContent>
            </Card>
          )}
        </div>
        <div className="grid content-start gap-4">
          <Card>
            <CardHeader>
              <CardTitle>
                <h2 className="text-base font-semibold">CV</h2>
              </CardTitle>
            </CardHeader>
            <CardContent className="grid gap-2 text-sm">
              {pkg.resume ? (
                <>
                  <p>
                    {pkg.resume.title} · v{pkg.resume.versionNo}{' '}
                    <StatusBadge tone={pkg.resume.status === 'APPROVED' ? 'success' : 'warning'}>{pkg.resume.status === 'APPROVED' ? 'Aprobado' : 'Borrador'}</StatusBadge>
                  </p>
                  <Button variant="outline" onClick={() => void downloadCv()} disabled={downloading}>
                    <Download aria-hidden /> Descargar CV en PDF
                  </Button>
                </>
              ) : (
                <p className="text-muted-foreground">
                  No elegiste un CV. <Link to={`/applications/${id}`} className="text-primary underline">Elegir CV</Link>
                </p>
              )}
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>
                <h2 className="text-base font-semibold">Lista de verificación</h2>
              </CardTitle>
            </CardHeader>
            <CardContent>
              <ul className="grid gap-1.5 text-sm" aria-label="Lista de verificación">
                {pkg.checklist.map((c) => (
                  <li key={c.key} className="flex items-center gap-2">
                    {c.done ? <CheckCircle2 className="size-4 text-emerald-600" aria-hidden /> : <Circle className="size-4 text-muted-foreground" aria-hidden />}
                    <span>
                      <span className="sr-only">{c.done ? 'Hecho: ' : 'Pendiente: '}</span>
                      {c.label}
                    </span>
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
        </div>
      </div>
      <MarkSubmittedDialog
        application={submitting}
        onClose={(updated) => {
          setSubmitting(null)
          if (updated) void app.refetch()
        }}
      />
    </>
  )
}
