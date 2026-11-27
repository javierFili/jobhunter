import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ArrowLeft, BadgeCheck, Check, Copy, Download, Loader2, Save, Trash2, X } from 'lucide-react'
import { toast } from 'sonner'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { ErrorState } from '@/components/ErrorState'
import { FormField } from '@/components/FormField'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { SelectField } from '@/components/SelectField'
import { StatusBadge } from '@/components/StatusBadge'
import { ValidationReportPanel } from '@/components/ValidationReportPanel'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { downloadBlob } from '@/features/resumes/editor/useObjectUrl'
import { resumeLanguageLabels } from '@/features/resumes/labels'
import { ApiError, errorMessage } from '@/lib/problem'
import { copyToClipboard } from '@/lib/clipboard'
import { cn } from '@/lib/utils'
import { useApproveLetter, useCoverLetter, useDeleteLetter, useLetterPdf, useLetterVersions, useSaveLetter } from './api'
import { HAS_SUBJECT, MAX_CHARS, formatLabels, removeSentence, toneLabels } from './labels'
import type { CoverLetterDto } from './types'

function LetterEditor({ letter }: { letter: CoverLetterDto }) {
  const navigate = useNavigate()
  const [subject, setSubject] = useState(letter.subject ?? '')
  const [body, setBody] = useState(letter.body)
  const [pending, setPending] = useState(letter.suggestions)
  const [deleting, setDeleting] = useState(false)
  const save = useSaveLetter(letter.id)
  const approve = useApproveLetter(letter.id)
  const remove = useDeleteLetter()
  const versions = useLetterVersions(letter.id)
  const pdf = useLetterPdf(letter.id, true)
  const hasSubject = HAS_SUBJECT.includes(letter.format)
  const max = MAX_CHARS[letter.format]
  const over = max != null && body.length > max
  const dirty = body !== letter.body || (hasSubject && subject !== (letter.subject ?? '')) || pending.length !== letter.suggestions.length

  const doSave = () =>
    save.mutate(
      { subject: hasSubject ? subject.trim() || null : undefined, body },
      {
        onSuccess: (l) => {
          toast.success(`Versión v${l.versionNo} guardada`)
          navigate(`/cover-letters/${l.id}`)
        },
        onError: (e) => toast.error(errorMessage(e)),
      },
    )

  const doApprove = () =>
    approve.mutate(undefined, {
      onSuccess: () => toast.success('Carta aprobada'),
      onError: (e) =>
        toast.error(
          e instanceof ApiError && e.code === 'UNCONFIRMED_SUGGESTIONS'
            ? 'Esta versión tiene sugerencias sin revisar. Acéptalas o quítalas y guarda una nueva versión antes de aprobar.'
            : errorMessage(e),
        ),
    })

  const copy = async () => {
    try {
      await copyToClipboard(hasSubject && subject ? `${subject}\n\n${body}` : body)
      toast.success('Copiado al portapapeles')
    } catch {
      toast.error('No se pudo copiar. Selecciona el texto y cópialo manualmente.')
    }
  }

  return (
    <div className="grid gap-4 lg:grid-cols-[1fr_20rem]">
      <div className="grid content-start gap-4">
        <div className="flex flex-wrap gap-2">
          <Button onClick={doSave} disabled={!dirty || save.isPending || !body.trim()}>
            {save.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <Save aria-hidden />}
            Guardar como nueva versión
          </Button>
          <Button variant="outline" onClick={doApprove} disabled={dirty || approve.isPending || letter.status === 'APPROVED'} title={dirty ? 'Guarda los cambios antes de aprobar' : undefined}>
            {approve.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <BadgeCheck aria-hidden />}
            Aprobar
          </Button>
          <Button variant="outline" onClick={() => void copy()}>
            <Copy aria-hidden /> Copiar al portapapeles
          </Button>
          <Button
            variant="outline"
            disabled={!pdf.data}
            aria-label={letter.status === 'APPROVED' ? 'Descargar PDF' : 'Descargar PDF (Borrador)'}
            onClick={() => pdf.data && downloadBlob(pdf.data, `carta-${letter.letterGroupId}-v${letter.versionNo}${letter.status === 'APPROVED' ? '' : '-borrador'}.pdf`)}
          >
            <Download aria-hidden /> Descargar PDF
            {letter.status !== 'APPROVED' && <span className="text-xs font-normal text-muted-foreground" aria-hidden>
                (Borrador)
              </span>}
          </Button>
          <Button variant="ghost" className="text-destructive" onClick={() => setDeleting(true)}>
            <Trash2 aria-hidden /> Eliminar
          </Button>
        </div>
        {hasSubject && (
          <FormField id="cl-subject" label="Asunto">
            <Input value={subject} onChange={(e) => setSubject(e.target.value)} />
          </FormField>
        )}
        <div className="grid gap-1.5">
          <Label htmlFor="cl-body">Texto</Label>
          <Textarea id="cl-body" rows={16} value={body} onChange={(e) => setBody(e.target.value)} aria-describedby="cl-body-count" />
          <p id="cl-body-count" aria-live="polite" className={cn('text-xs', over ? 'font-medium text-destructive' : 'text-muted-foreground')}>
            {body.length} caracteres{max != null && ` · máximo recomendado ${max}`}
            {over && ` — supera el límite recomendado para ${formatLabels[letter.format].toLowerCase()}`}
          </p>
        </div>
        {letter.validationReport && <ValidationReportPanel report={letter.validationReport} showPrinciple={false} />}
      </div>
      <div className="grid content-start gap-4">
        <Card>
          <CardHeader>
            <CardTitle>
              <h2 className="text-base font-semibold">Sugerencias por revisar</h2>
            </CardTitle>
            <CardDescription>Frases que la IA no pudo respaldar con tu perfil verificado.</CardDescription>
          </CardHeader>
          <CardContent>
            {pending.length === 0 ? (
              <p className="text-sm text-muted-foreground">No hay sugerencias pendientes.</p>
            ) : (
              <ul className="grid gap-2" aria-label="Sugerencias">
                {pending.map((s, i) => (
                  <li key={i} className="grid gap-1.5 rounded-lg border border-amber-300 bg-amber-50 p-2 text-sm text-amber-950 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-100">
                    <p>«{s.text}»</p>
                    {s.note && <p className="text-xs">Motivo: {s.note}</p>}
                    <div className="flex gap-1">
                      <Button size="xs" variant="outline" onClick={() => setPending((p) => p.filter((_, j) => j !== i))} aria-label={`Aceptar sugerencia ${i + 1}`}>
                        <Check aria-hidden /> Aceptar
                      </Button>
                      <Button
                        size="xs"
                        variant="destructive"
                        onClick={() => {
                          setBody((b) => removeSentence(b, s.text))
                          setPending((p) => p.filter((_, j) => j !== i))
                        }}
                        aria-label={`Quitar sugerencia ${i + 1}`}
                      >
                        <X aria-hidden /> Quitar
                      </Button>
                    </div>
                  </li>
                ))}
              </ul>
            )}
            {letter.suggestions.length > 0 && pending.length === 0 && (
              <p className="mt-2 text-xs text-muted-foreground">Guarda una nueva versión para registrar tu revisión.</p>
            )}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>
              <h2 className="text-base font-semibold">Versiones</h2>
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid gap-1.5">
              <Label htmlFor="cl-version">Versión</Label>
              <SelectField
                id="cl-version"
                value={String(letter.id)}
                onChange={(v) => v && navigate(`/cover-letters/${v}`)}
                options={(versions.data ?? [{ id: letter.id, versionNo: letter.versionNo, status: letter.status }]).map((v) => ({
                  value: String(v.id),
                  label: `v${v.versionNo} · ${v.status === 'APPROVED' ? 'Aprobada' : 'Borrador'}`,
                }))}
              />
            </div>
          </CardContent>
        </Card>
      </div>
      <ConfirmDialog
        open={deleting}
        onOpenChange={setDeleting}
        title="¿Eliminar esta carta?"
        description="Se eliminarán todas sus versiones."
        confirmLabel="Eliminar"
        destructive
        pending={remove.isPending}
        onConfirm={() =>
          remove.mutate(letter.id, {
            onSuccess: () => {
              toast.success('Carta eliminada')
              navigate('/cover-letters')
            },
            onError: (e) => toast.error(errorMessage(e)),
          })
        }
      />
    </div>
  )
}

export function CoverLetterEditorPage() {
  const id = Number(useParams().letterId)
  const { data, isLoading, error, refetch } = useCoverLetter(id)
  if (isLoading) return <LoadingState />
  if (error || !data) return <ErrorState error={error} onRetry={() => void refetch()} />
  return (
    <>
      <Button asChild variant="ghost" size="sm" className="mb-2 -ml-2">
        <Link to="/cover-letters">
          <ArrowLeft aria-hidden /> Cartas de presentación
        </Link>
      </Button>
      <PageHeader
        title={`${formatLabels[data.format]} · v${data.versionNo}`}
        description={[resumeLanguageLabels[data.language], data.tone ? `Tono ${toneLabels[data.tone].toLowerCase()}` : null, data.provider ? `Generada con ${data.provider} ${data.model ?? ''}` : null]
          .filter(Boolean)
          .join(' · ')}
        actions={
          <div className="flex flex-wrap gap-2">
            <StatusBadge tone={data.status === 'APPROVED' ? 'success' : 'warning'}>{data.status === 'APPROVED' ? 'Aprobada' : 'Borrador'}</StatusBadge>
            {data.jobOfferId && (
              <Button asChild size="sm" variant="outline">
                <Link to={`/jobs/${data.jobOfferId}`}>Ver oferta</Link>
              </Button>
            )}
          </div>
        }
      />
      <LetterEditor key={data.id} letter={data} />
    </>
  )
}
