import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { AlertTriangle, ArrowLeft, CheckCircle2, Info, Loader2, X } from 'lucide-react'
import { toast } from 'sonner'
import { ErrorState } from '@/components/ErrorState'
import { FormField } from '@/components/FormField'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { SelectField } from '@/components/SelectField'
import { StatusBadge } from '@/components/StatusBadge'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  certificationSchema,
  certificationToRequest,
  educationSchema,
  educationToRequest,
  experienceSchema,
  experienceToRequest,
  projectSchema,
  projectToRequest,
} from '@/features/profile/schemas'
import { formatDateTime } from '@/lib/format'
import {
  LANGUAGE_CODES,
  educationStatusLabels,
  employmentTypeLabels,
  languageLevelLabels,
  languageName,
  projectKindLabels,
  toOptions,
  workModalityLabels,
} from '@/lib/labels'
import { errorMessage } from '@/lib/problem'
import type { LanguageLevel, SkillRef } from '@/lib/types'
import { z } from 'zod'
import { useAiParseStatus, useImportUpload, useStartAiParse, useUpload } from '../api'
import type { ImportResult, UploadDto } from '../types'
import { ReviewCard } from './ReviewCard'
import { TaskStatus } from '@/components/TaskStatus'
import { apiErrorCode, taskErrorMessage, useOnTaskFinished } from '@/lib/task'
import { useQueryClient } from '@tanstack/react-query'
import { Sparkles } from 'lucide-react'
import { buildReviewState, validateItems, type ReviewItem, type ReviewState } from './reviewState'

const languageSchema = z.object({
  languageCode: z.string().regex(/^[a-z]{2}$/, 'Elige el idioma'),
  level: z.enum(['A1', 'A2', 'B1', 'B2', 'C1', 'C2', 'NATIVE'], { error: 'Elige el nivel' }),
  certificate: z.string(),
})

const employmentOptions = toOptions(employmentTypeLabels)
const modalityOptions = toOptions(workModalityLabels)
const kindOptions = toOptions(projectKindLabels)
const statusOptions = toOptions(educationStatusLabels)
const levelOptions = toOptions(languageLevelLabels)
const languageOptions = LANGUAGE_CODES.map((c) => ({ value: c as string, label: languageName(c) }))

type ListKey = 'experiences' | 'projects' | 'educations' | 'certifications' | 'languages'

function SkillBadges({ skills, onRemove }: { skills: SkillRef[]; onRemove: (id: number) => void }) {
  if (!skills.length) return null
  return (
    <div className="grid gap-1.5">
      <span className="text-sm font-medium">Habilidades detectadas</span>
      <ul className="flex flex-wrap gap-1.5">
        {skills.map((s) => (
          <li key={s.id}>
            <Badge variant="secondary" className="h-6 gap-1 pr-1">
              {s.name}
              <button type="button" onClick={() => onRemove(s.id)} aria-label={`Quitar ${s.name}`} className="rounded-full p-0.5 hover:bg-foreground/10">
                <X className="size-3" aria-hidden />
              </button>
            </Badge>
          </li>
        ))}
      </ul>
    </div>
  )
}

function Section({ title, count, children }: { title: string; count: number; children: React.ReactNode }) {
  if (count === 0) return null
  return (
    <section aria-label={title} className="grid gap-3">
      <h2 className="text-lg font-semibold">
        {title} <span className="text-sm font-normal text-muted-foreground">({count})</span>
      </h2>
      {children}
    </section>
  )
}

function ReviewEditor({ upload }: { upload: UploadDto }) {
  const [state, setState] = useState<ReviewState>(() => buildReviewState(upload.parsed))
  const [result, setResult] = useState<ImportResult | null>(null)
  const importMutation = useImportUpload(upload.id)
  const parsed = upload.parsed
  const contact = parsed?.contact
  const warnings = parsed?.warnings ?? []

  const updateItem = <K extends ListKey>(list: K, key: string, patch: Partial<ReviewState[K][number]['values']>) =>
    setState((s) => ({
      ...s,
      [list]: (s[list] as ReviewItem<object>[]).map((it) =>
        it.key === key ? { ...it, values: { ...it.values, ...patch }, errors: Object.fromEntries(Object.entries(it.errors).filter(([k]) => !(k in patch))) } : it,
      ),
    }))
  const toggleItem = (list: ListKey, key: string, selected: boolean) =>
    setState((s) => ({ ...s, [list]: (s[list] as ReviewItem<object>[]).map((it) => (it.key === key ? { ...it, selected } : it)) }))

  const selectedCount =
    state.experiences.filter((i) => i.selected).length +
    state.projects.filter((i) => i.selected).length +
    state.educations.filter((i) => i.selected).length +
    state.certifications.filter((i) => i.selected).length +
    state.skills.filter((i) => i.selected).length +
    state.languages.filter((i) => i.selected).length

  const submit = () => {
    const exp = validateItems(state.experiences, experienceSchema)
    const prj = validateItems(state.projects, projectSchema)
    const edu = validateItems(state.educations, educationSchema)
    const cert = validateItems(state.certifications, certificationSchema)
    const lang = validateItems(state.languages, languageSchema)
    setState((s) => ({ ...s, experiences: exp.items, projects: prj.items, educations: edu.items, certifications: cert.items, languages: lang.items }))
    if (!exp.ok || !prj.ok || !edu.ok || !cert.ok || !lang.ok) {
      toast.error('Revisa los elementos marcados en rojo antes de importar.')
      return
    }
    importMutation.mutate(
      {
        experiences: exp.parsed.map(experienceToRequest),
        projects: prj.parsed.map(projectToRequest),
        educations: edu.parsed.map(educationToRequest),
        certifications: cert.parsed.map(certificationToRequest),
        skillIds: state.skills.filter((s) => s.selected).map((s) => s.skillId),
        languages: lang.parsed.map((l) => ({ languageCode: l.languageCode, level: l.level as LanguageLevel, certificate: l.certificate.trim() || null })),
      },
      {
        onSuccess: (r) => {
          setResult(r)
          toast.success('Importación completada. Revisa y verifica los elementos en Mi perfil.')
        },
        onError: (e) => toast.error(errorMessage(e)),
      },
    )
  }

  if (result) {
    const total = Object.values(result).reduce((a, b) => a + b, 0)
    return (
      <Card>
        <CardHeader>
          <CardTitle>
            <h2 className="flex items-center gap-2 text-lg font-semibold">
              <CheckCircle2 className="size-5 text-emerald-600" aria-hidden /> Se importaron {total} elementos
            </h2>
          </CardTitle>
          <CardDescription>Todo lo importado queda Sin verificar hasta que lo revises.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          <ul className="grid grid-cols-2 gap-2 text-sm sm:grid-cols-3">
            <li>Experiencias: {result.experiences}</li>
            <li>Proyectos: {result.projects}</li>
            <li>Formación: {result.educations}</li>
            <li>Certificaciones: {result.certifications}</li>
            <li>Habilidades: {result.skills}</li>
            <li>Idiomas: {result.languages}</li>
          </ul>
          <div className="flex flex-wrap gap-2">
            <Button asChild>
              <Link to="/profile?tab=experience">Ir a Mi perfil para verificar</Link>
            </Button>
            <Button asChild variant="outline">
              <Link to="/cvs">Volver a Mis CV</Link>
            </Button>
          </div>
        </CardContent>
      </Card>
    )
  }

  const noItems =
    !state.experiences.length && !state.projects.length && !state.educations.length && !state.certifications.length && !state.skills.length && !state.languages.length

  return (
    <div className="grid gap-6 pb-24">
      <div className="flex items-start gap-2 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-950 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-100" role="note">
        <Info className="mt-0.5 size-4 shrink-0" aria-hidden />
        <p>
          <strong>Todo lo importado queda Sin verificar hasta que lo revises</strong> en Mi perfil. Revisa cada elemento: la
          extracción automática puede equivocarse.
        </p>
      </div>
      {upload.importedAt && (
        <p className="text-sm text-muted-foreground">Este CV ya se importó el {formatDateTime(upload.importedAt)}. Importarlo de nuevo puede crear duplicados.</p>
      )}
      {warnings.length > 0 && (
        <div role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm">
          <p className="mb-1 flex items-center gap-1.5 font-medium text-destructive">
            <AlertTriangle className="size-4" aria-hidden /> Advertencias del análisis
          </p>
          <ul className="list-disc pl-5">
            {warnings.map((w, i) => (
              <li key={i}>{w}</li>
            ))}
          </ul>
        </div>
      )}
      {contact && (contact.fullName || contact.email) && (
        <Card>
          <CardHeader>
            <CardTitle>
              <h2 className="text-base font-semibold">Datos de contacto detectados</h2>
            </CardTitle>
            <CardDescription>No se importan automáticamente; edítalos en tus perfiles profesionales si es necesario.</CardDescription>
          </CardHeader>
          <CardContent>
            <dl className="grid gap-x-6 gap-y-1 text-sm sm:grid-cols-2">
              {(
                [
                  ['Nombre', contact.fullName],
                  ['Correo', contact.email],
                  ['Teléfono', contact.phone],
                  ['Ubicación', contact.location],
                  ['GitHub', contact.githubUrl],
                  ['LinkedIn', contact.linkedinUrl],
                  ['Sitio web', contact.websiteUrl],
                ] as const
              )
                .filter(([, v]) => !!v)
                .map(([k, v]) => (
                  <div key={k} className="flex gap-2">
                    <dt className="text-muted-foreground">{k}:</dt>
                    <dd className="truncate">{v}</dd>
                  </div>
                ))}
            </dl>
          </CardContent>
        </Card>
      )}
      {noItems && <p className="text-sm text-muted-foreground">No se detectaron elementos para importar en este CV.</p>}

      <Section title="Experiencia laboral" count={state.experiences.length}>
        {state.experiences.map((it) => {
          const v = it.values
          const id = it.key
          const set = (patch: Partial<typeof v>) => updateItem('experiences', it.key, patch)
          return (
            <ReviewCard
              key={it.key}
              id={id}
              title={[v.positionTitle, v.companyName].filter(Boolean).join(' · ') || 'Experiencia sin título'}
              selected={it.selected}
              onSelectedChange={(s) => toggleItem('experiences', it.key, s)}
              rawText={it.rawText}
              hasErrors={Object.keys(it.errors).length > 0}
            >
              <div className="grid gap-3 sm:grid-cols-2">
                <FormField id={`${id}-position`} label="Cargo" required error={it.errors.positionTitle}>
                  <Input value={v.positionTitle} onChange={(e) => set({ positionTitle: e.target.value })} />
                </FormField>
                <FormField id={`${id}-company`} label="Empresa" required error={it.errors.companyName}>
                  <Input value={v.companyName} onChange={(e) => set({ companyName: e.target.value })} />
                </FormField>
                <FormField id={`${id}-type`} label="Tipo de empleo" required error={it.errors.employmentType} hint="Elígelo tú: el CV no lo indica de forma fiable.">
                  <SelectField value={v.employmentType} onChange={(t) => set({ employmentType: t })} options={employmentOptions} placeholder="Elige el tipo…" />
                </FormField>
                <FormField id={`${id}-modality`} label="Modalidad" required error={it.errors.workModality}>
                  <SelectField value={v.workModality} onChange={(m) => set({ workModality: m })} options={modalityOptions} />
                </FormField>
                <FormField id={`${id}-start`} label="Fecha de inicio" required error={it.errors.startDate}>
                  <Input type="date" value={v.startDate} onChange={(e) => set({ startDate: e.target.value })} />
                </FormField>
                <div className="grid gap-2">
                  <FormField id={`${id}-end`} label="Fecha de fin" error={it.errors.endDate}>
                    <Input type="date" value={v.endDate ?? ''} disabled={v.current} onChange={(e) => set({ endDate: e.target.value })} />
                  </FormField>
                  <div className="flex items-center gap-2">
                    <Checkbox id={`${id}-current`} checked={v.current} onCheckedChange={(c) => set({ current: c === true, ...(c === true ? { endDate: '' } : {}) })} />
                    <Label htmlFor={`${id}-current`} className="font-normal">
                      Trabajo aquí actualmente
                    </Label>
                  </div>
                </div>
              </div>
              <FormField id={`${id}-description`} label="Descripción" error={it.errors.description}>
                <Textarea rows={2} value={v.description} onChange={(e) => set({ description: e.target.value })} />
              </FormField>
              <FormField id={`${id}-achievements`} label="Logros" hint="Uno por línea." error={it.errors.achievements}>
                <Textarea rows={2} value={v.achievements} onChange={(e) => set({ achievements: e.target.value })} />
              </FormField>
              <SkillBadges skills={v.skills} onRemove={(sid) => set({ skills: v.skills.filter((s) => s.id !== sid) })} />
            </ReviewCard>
          )
        })}
      </Section>

      <Section title="Proyectos" count={state.projects.length}>
        {state.projects.map((it) => {
          const v = it.values
          const id = it.key
          const set = (patch: Partial<typeof v>) => updateItem('projects', it.key, patch)
          return (
            <ReviewCard
              key={it.key}
              id={id}
              title={v.name || 'Proyecto sin nombre'}
              badge={<StatusBadge tone="violet">Proyecto · {v.kind ? projectKindLabels[v.kind] : 'sin tipo'}</StatusBadge>}
              selected={it.selected}
              onSelectedChange={(s) => toggleItem('projects', it.key, s)}
              rawText={it.rawText}
              hasErrors={Object.keys(it.errors).length > 0}
            >
              <div className="grid gap-3 sm:grid-cols-2">
                <FormField id={`${id}-kind`} label="Tipo de proyecto" required error={it.errors.kind} hint="Un proyecto nunca es un empleo.">
                  <SelectField value={v.kind} onChange={(k) => set({ kind: k })} options={kindOptions} />
                </FormField>
                <FormField id={`${id}-name`} label="Nombre" required error={it.errors.name}>
                  <Input value={v.name} onChange={(e) => set({ name: e.target.value })} />
                </FormField>
              </div>
              <FormField id={`${id}-description`} label="Descripción" error={it.errors.description}>
                <Textarea rows={2} value={v.description} onChange={(e) => set({ description: e.target.value })} />
              </FormField>
              <SkillBadges skills={v.skills} onRemove={(sid) => set({ skills: v.skills.filter((s) => s.id !== sid) })} />
            </ReviewCard>
          )
        })}
      </Section>

      <Section title="Educación" count={state.educations.length}>
        {state.educations.map((it) => {
          const v = it.values
          const id = it.key
          const set = (patch: Partial<typeof v>) => updateItem('educations', it.key, patch)
          return (
            <ReviewCard
              key={it.key}
              id={id}
              title={v.institution || 'Formación sin institución'}
              selected={it.selected}
              onSelectedChange={(s) => toggleItem('educations', it.key, s)}
              rawText={it.rawText}
              hasErrors={Object.keys(it.errors).length > 0}
            >
              <div className="grid gap-3 sm:grid-cols-2">
                <FormField id={`${id}-institution`} label="Institución" required error={it.errors.institution}>
                  <Input value={v.institution} onChange={(e) => set({ institution: e.target.value })} />
                </FormField>
                <FormField id={`${id}-degree`} label="Título" error={it.errors.degree}>
                  <Input value={v.degree} onChange={(e) => set({ degree: e.target.value })} />
                </FormField>
                <FormField id={`${id}-start`} label="Fecha de inicio" error={it.errors.startDate}>
                  <Input type="date" value={v.startDate} onChange={(e) => set({ startDate: e.target.value })} />
                </FormField>
                <FormField id={`${id}-end`} label="Fecha de fin" error={it.errors.endDate}>
                  <Input type="date" value={v.endDate} onChange={(e) => set({ endDate: e.target.value })} />
                </FormField>
                <FormField id={`${id}-status`} label="Estado" required error={it.errors.status}>
                  <SelectField value={v.status} onChange={(st) => set({ status: st })} options={statusOptions} />
                </FormField>
              </div>
            </ReviewCard>
          )
        })}
      </Section>

      <Section title="Certificaciones" count={state.certifications.length}>
        {state.certifications.map((it) => {
          const v = it.values
          const id = it.key
          const set = (patch: Partial<typeof v>) => updateItem('certifications', it.key, patch)
          return (
            <ReviewCard
              key={it.key}
              id={id}
              title={v.name || 'Certificación sin nombre'}
              selected={it.selected}
              onSelectedChange={(s) => toggleItem('certifications', it.key, s)}
              rawText={it.rawText}
              hasErrors={Object.keys(it.errors).length > 0}
            >
              <div className="grid gap-3 sm:grid-cols-3">
                <FormField id={`${id}-name`} label="Nombre" required error={it.errors.name}>
                  <Input value={v.name} onChange={(e) => set({ name: e.target.value })} />
                </FormField>
                <FormField id={`${id}-issuer`} label="Entidad emisora" error={it.errors.issuer}>
                  <Input value={v.issuer} onChange={(e) => set({ issuer: e.target.value })} />
                </FormField>
                <FormField id={`${id}-issued`} label="Fecha de emisión" error={it.errors.issuedAt}>
                  <Input type="date" value={v.issuedAt} onChange={(e) => set({ issuedAt: e.target.value })} />
                </FormField>
              </div>
            </ReviewCard>
          )
        })}
      </Section>

      {state.skills.length > 0 && (
        <section aria-label="Habilidades" className="grid gap-3">
          <h2 className="text-lg font-semibold">
            Habilidades <span className="text-sm font-normal text-muted-foreground">({state.skills.length})</span>
          </h2>
          <p className="text-sm text-muted-foreground">
            Se agregan con nivel «Solo proyectos» y Sin verificar. Ajusta el nivel de evidencia real (por ejemplo «Experiencia
            profesional») al verificarlas en Mi perfil.
          </p>
          <ul className="flex flex-wrap gap-2">
            {state.skills.map((s) => (
              <li key={s.skillId} className="flex items-center gap-2 rounded-full border px-3 py-1">
                <Checkbox
                  id={`skill-${s.skillId}`}
                  checked={s.selected}
                  onCheckedChange={(c) => setState((st) => ({ ...st, skills: st.skills.map((x) => (x.skillId === s.skillId ? { ...x, selected: c === true } : x)) }))}
                />
                <Label htmlFor={`skill-${s.skillId}`} className="font-normal">
                  {s.name}
                </Label>
              </li>
            ))}
          </ul>
        </section>
      )}

      <Section title="Idiomas" count={state.languages.length}>
        {state.languages.map((it) => {
          const v = it.values
          const id = it.key
          const set = (patch: Partial<typeof v>) => updateItem('languages', it.key, patch)
          return (
            <ReviewCard
              key={it.key}
              id={id}
              title={v.languageCode ? languageName(v.languageCode) : 'Idioma sin identificar'}
              selected={it.selected}
              onSelectedChange={(s) => toggleItem('languages', it.key, s)}
              rawText={it.rawText}
              hasErrors={Object.keys(it.errors).length > 0}
            >
              <div className="grid gap-3 sm:grid-cols-3">
                <FormField id={`${id}-code`} label="Idioma" required error={it.errors.languageCode}>
                  <SelectField value={v.languageCode} onChange={(c) => set({ languageCode: c })} options={languageOptions} />
                </FormField>
                <FormField id={`${id}-level`} label="Nivel" required error={it.errors.level}>
                  <SelectField value={v.level} onChange={(l) => set({ level: l })} options={levelOptions} placeholder="Elige el nivel…" />
                </FormField>
                <FormField id={`${id}-cert`} label="Certificado" error={it.errors.certificate}>
                  <Input value={v.certificate} onChange={(e) => set({ certificate: e.target.value })} />
                </FormField>
              </div>
            </ReviewCard>
          )
        })}
      </Section>

      <div className="fixed inset-x-0 bottom-0 z-10 border-t bg-background/95 backdrop-blur md:left-(--sidebar-width)">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-3 px-4 py-3 md:px-8">
          <span className="text-sm text-muted-foreground">{selectedCount} elementos seleccionados</span>
          <Button onClick={submit} disabled={selectedCount === 0 || importMutation.isPending}>
            {importMutation.isPending && <Loader2 className="animate-spin" aria-hidden />}
            Importar seleccionados
          </Button>
        </div>
      </div>
    </div>
  )
}

function AiParseCard({ uploadId, parser, onImproved }: { uploadId: number; parser: 'HEURISTIC' | 'AI'; onImproved: () => void }) {
  const qc = useQueryClient()
  const status = useAiParseStatus(uploadId)
  const start = useStartAiParse(uploadId)
  const running = status.data?.state === 'RUNNING' || start.isPending
  useOnTaskFinished(status.data?.state, (state) => {
    if (state === 'SUCCEEDED') {
      void qc.invalidateQueries({ queryKey: ['resumes', 'uploads', uploadId], exact: true }).then(onImproved)
      toast.success('CV reestructurado con IA. Revisa de nuevo los elementos detectados.')
    }
  })
  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="flex items-center gap-2 text-base font-semibold">
              <Sparkles className="size-4 text-primary" aria-hidden /> Mejorar con IA
            </h2>
            <Button
              size="sm"
              variant={parser === 'AI' ? 'outline' : 'default'}
              disabled={running}
              onClick={() =>
                start.mutate(undefined, {
                  onError: (e) => {
                    const code = apiErrorCode(e)
                    toast.error(code ? taskErrorMessage(code, errorMessage(e)) : errorMessage(e))
                  },
                })
              }
            >
              {running ? <Loader2 className="animate-spin" aria-hidden /> : <Sparkles aria-hidden />}
              {parser === 'AI' ? 'Volver a analizar con IA' : 'Mejorar con IA'}
            </Button>
          </div>
        </CardTitle>
        <CardDescription>
          Se envía el texto del CV sin correos, teléfonos ni enlaces al proveedor configurado; requiere tu consentimiento
          «Procesar CV». Los cambios que hayas hecho en esta pantalla se reemplazarán por el nuevo análisis.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <TaskStatus task={status.data} runningText="Estructurando tu CV con IA…" />
      </CardContent>
    </Card>
  )
}

export function ImportReviewPage() {
  const id = Number(useParams().uploadId)
  const { data, isLoading, error, refetch } = useUpload(id)
  const [generation, setGeneration] = useState(0)
  return (
    <>
      <Button asChild variant="ghost" size="sm" className="mb-2 -ml-2">
        <Link to="/cvs?tab=import">
          <ArrowLeft aria-hidden /> Mis CV
        </Link>
      </Button>
      <PageHeader
        title="Revisar CV importado"
        description="Marca qué quieres agregar a tu perfil y corrige lo que se haya detectado mal."
        actions={data && <StatusBadge tone="neutral">{data.parser === 'AI' ? 'Análisis con IA' : 'Análisis automático'}</StatusBadge>}
      />
      {isLoading ? <LoadingState /> : error || !data ? <ErrorState error={error} onRetry={() => void refetch()} /> : (
        <div className="grid gap-6">
          <AiParseCard uploadId={data.id} parser={data.parser} onImproved={() => setGeneration((g) => g + 1)} />
          <ReviewEditor key={`${data.id}-${generation}`} upload={data} />
        </div>
      )}
    </>
  )
}
