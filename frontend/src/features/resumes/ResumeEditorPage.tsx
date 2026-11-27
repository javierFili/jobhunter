import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { ArrowLeft, BadgeCheck, Download, Loader2, Save } from 'lucide-react'
import { toast } from 'sonner'
import { ErrorState } from '@/components/ErrorState'
import { FormField } from '@/components/FormField'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { SelectField } from '@/components/SelectField'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import { FormDialog } from '@/features/profile/components/FormDialog'
import { ApiError, errorMessage } from '@/lib/problem'
import {
  useApproveVersion,
  useResume,
  useResumePdf,
  useResumeTemplates,
  useSaveVersion,
  useUpdateResume,
  useVersion,
  useVersions,
} from './api'
import { CompareView } from './editor/CompareView'
import { DocumentEditor } from './editor/DocumentEditor'
import { PdfPreview } from './editor/PdfPreview'
import { downloadBlob } from './editor/useObjectUrl'
import { VersionHistory } from './editor/VersionHistory'
import { resumeLanguageLabels, versionStatusLabels, versionStatusTone } from './labels'
import type { ResumeDocument, ResumeDto, TemplateId, ValidationReport, VersionSummaryDto } from './types'
import { ValidationReportPanel } from '@/components/ValidationReportPanel'
import { TRUTH_PRINCIPLE } from '@/lib/truth'

function countSuggested(doc: ResumeDocument): number {
  return [...doc.experience, ...doc.projects].reduce((n, e) => n + e.bullets.filter((b) => b.status === 'SUGGESTED').length, 0)
}

function SaveDialog({ open, onOpenChange, onSave, pending }: { open: boolean; onOpenChange: (o: boolean) => void; onSave: (note: string) => void; pending: boolean }) {
  const [note, setNote] = useState('')
  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title="Guardar como nueva versión"
      description="Cada guardado crea una versión nueva; las anteriores no se modifican."
      submitLabel="Guardar versión"
      submitting={pending}
      onSubmit={(e) => {
        e.preventDefault()
        onSave(note.trim())
      }}
    >
      <FormField id="version-note" label="Nota (opcional)" hint="Ej.: «Ajusté el resumen para backend».">
        <Textarea rows={3} value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} />
      </FormField>
    </FormDialog>
  )
}

function EditorWorkspace({
  resume,
  summary,
  content,
  report,
  onSaved,
}: {
  resume: ResumeDto
  summary: VersionSummaryDto
  content: ResumeDocument
  report: ValidationReport | null
  onSaved: (versionNo: number) => void
}) {
  const [doc, setDoc] = useState<ResumeDocument>(content)
  const [saveOpen, setSaveOpen] = useState(false)
  const save = useSaveVersion(resume.id)
  const approve = useApproveVersion(resume.id)
  const dirty = JSON.stringify(doc) !== JSON.stringify(content)
  const suggested = countSuggested(doc)
  const pdf = useResumePdf(resume.id, summary.versionNo, resume.templateId)

  const doSave = (note: string) =>
    save.mutate(
      { content: doc, baseVersionNo: summary.versionNo, ...(note ? { note } : {}) },
      {
        onSuccess: (v) => {
          toast.success(`Versión v${v.versionNo} guardada`)
          setSaveOpen(false)
          onSaved(v.versionNo)
        },
        onError: (e) => toast.error(errorMessage(e)),
      },
    )

  const doApprove = () =>
    approve.mutate(summary.versionNo, {
      onSuccess: () => toast.success(`Versión v${summary.versionNo} aprobada`),
      onError: (e) =>
        toast.error(
          e instanceof ApiError && e.code === 'UNCONFIRMED_SUGGESTIONS'
            ? 'Confirma o elimina las sugerencias resaltadas en ámbar antes de aprobar.'
            : errorMessage(e),
        ),
    })

  return (
    <>
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <Button onClick={() => setSaveOpen(true)} disabled={!dirty || save.isPending}>
          <Save aria-hidden /> Guardar como nueva versión
        </Button>
        <Button
          variant="outline"
          onClick={doApprove}
          disabled={dirty || approve.isPending || summary.status === 'APPROVED'}
          title={dirty ? 'Guarda los cambios antes de aprobar' : undefined}
        >
          {approve.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <BadgeCheck aria-hidden />}
          Aprobar
        </Button>
        <Button
          variant="outline"
          disabled={!pdf.data}
          onClick={() => pdf.data && downloadBlob(pdf.data, `${resume.title.replace(/[^\w\- ]+/g, '').trim() || 'cv'}-v${summary.versionNo}.pdf`)}
        >
          <Download aria-hidden /> Descargar PDF
        </Button>
        {dirty && <StatusBadge tone="warning">Cambios sin guardar</StatusBadge>}
        {suggested > 0 && (
          <StatusBadge tone="warning">
            {suggested} {suggested === 1 ? 'sugerencia sin confirmar' : 'sugerencias sin confirmar'}
          </StatusBadge>
        )}
      </div>
      {(summary.origin === 'OPTIMIZED' || report) && (
        <div className="mb-4">
          {report ? (
            <ValidationReportPanel report={report} />
          ) : (
            <p className="rounded-lg border px-3 py-2 text-sm text-muted-foreground">{TRUTH_PRINCIPLE}</p>
          )}
        </div>
      )}
      <div className="grid gap-6 xl:grid-cols-2">
        <DocumentEditor doc={doc} onChange={setDoc} />
        <div className="xl:sticky xl:top-20 xl:self-start">
          <PdfPreview resumeId={resume.id} versionNo={summary.versionNo} template={resume.templateId} />
        </div>
      </div>
      <SaveDialog key={String(saveOpen)} open={saveOpen} onOpenChange={setSaveOpen} onSave={doSave} pending={save.isPending} />
    </>
  )
}

export function ResumeEditorPage() {
  const resumeId = Number(useParams().resumeId)
  const [params, setParams] = useSearchParams()
  const resume = useResume(resumeId)
  const versions = useVersions(resumeId)
  const templates = useResumeTemplates()
  const updateResume = useUpdateResume(resumeId)

  const latest = versions.data?.[0]?.versionNo ?? null
  const requested = params.get('v') ? Number(params.get('v')) : null
  const versionNo = requested ?? latest
  const version = useVersion(resumeId, versionNo)
  const tab = params.get('tab') ?? 'editor'

  const setParam = (patch: Record<string, string | null>) => {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(patch)) {
      if (v === null) next.delete(k)
      else next.set(k, v)
    }
    setParams(next, { replace: true })
  }

  if (resume.isLoading || versions.isLoading) return <LoadingState />
  if (resume.error || !resume.data) return <ErrorState error={resume.error} onRetry={() => void resume.refetch()} />
  if (versions.error) return <ErrorState error={versions.error} onRetry={() => void versions.refetch()} />

  const r = resume.data
  const list = versions.data ?? []
  const current = list.find((v) => v.versionNo === versionNo)

  return (
    <>
      <Button asChild variant="ghost" size="sm" className="mb-2 -ml-2">
        <Link to="/cvs">
          <ArrowLeft aria-hidden /> Mis CV
        </Link>
      </Button>
      <PageHeader
        title={r.title}
        description={`Idioma: ${resumeLanguageLabels[r.language]}`}
        actions={
          <div className="flex flex-wrap items-end gap-3">
            <div className="grid w-44 gap-1.5">
              <Label htmlFor="version-select">Versión</Label>
              <SelectField
                id="version-select"
                value={versionNo != null ? String(versionNo) : ''}
                onChange={(v) => setParam({ v: v || null })}
                options={list.map((v) => ({ value: String(v.versionNo), label: `v${v.versionNo} · ${versionStatusLabels[v.status]}` }))}
              />
            </div>
            <div className="grid w-40 gap-1.5">
              <Label htmlFor="template-select">Plantilla</Label>
              <SelectField
                id="template-select"
                value={r.templateId}
                disabled={updateResume.isPending}
                onChange={(t) =>
                  t &&
                  updateResume.mutate(
                    { templateId: t as TemplateId, version: r.version },
                    { onSuccess: () => toast.success('Plantilla actualizada'), onError: (e) => toast.error(errorMessage(e)) },
                  )
                }
                options={(templates.data ?? []).map((t) => ({ value: t.id, label: t.name }))}
              />
            </div>
          </div>
        }
      />
      {current && (
        <div className="mb-4 flex flex-wrap items-center gap-2 text-sm">
          <StatusBadge tone={versionStatusTone[current.status]}>{versionStatusLabels[current.status]}</StatusBadge>
          {current.hasPdf && <StatusBadge tone="teal">PDF aprobado</StatusBadge>}
          {current.note && <span className="text-muted-foreground">Nota: {current.note}</span>}
        </div>
      )}
      <Tabs value={tab} onValueChange={(t) => setParam({ tab: t === 'editor' ? null : t })}>
        <TabsList>
          <TabsTrigger value="editor">Editor</TabsTrigger>
          <TabsTrigger value="history">Historial</TabsTrigger>
          <TabsTrigger value="compare">Comparar</TabsTrigger>
        </TabsList>
        <TabsContent value="editor" className="pt-4">
          {version.isLoading || !versionNo ? (
            <LoadingState />
          ) : version.error || !version.data ? (
            <ErrorState error={version.error} onRetry={() => void version.refetch()} />
          ) : (
            <EditorWorkspace
              key={`${version.data.summary.id}`}
              resume={r}
              summary={version.data.summary}
              content={version.data.content}
              report={version.data.validationReport}
              onSaved={(no) => setParam({ v: String(no) })}
            />
          )}
        </TabsContent>
        <TabsContent value="history" className="pt-4">
          <VersionHistory versions={list} current={versionNo} onOpen={(no) => setParam({ v: String(no), tab: null })} />
        </TabsContent>
        <TabsContent value="compare" className="pt-4">
          <CompareView
            resumeId={resumeId}
            versions={list}
            initialTo={params.get('to') ? Number(params.get('to')) : null}
            initialFromId={params.get('fromId') ? Number(params.get('fromId')) : null}
          />
        </TabsContent>
      </Tabs>
    </>
  )
}
