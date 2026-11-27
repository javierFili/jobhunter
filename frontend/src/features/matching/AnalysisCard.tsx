import { AlertTriangle, Loader2, RefreshCw, Sparkles } from 'lucide-react'
import { toast } from 'sonner'
import { StatusBadge } from '@/components/StatusBadge'
import { TaskStatus } from '@/components/TaskStatus'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { languageName } from '@/lib/labels'
import { errorMessage } from '@/lib/problem'
import { apiErrorCode, taskErrorMessage } from '@/lib/task'
import { useAnalyzeJob, useJobAnalysis } from './api'
import { analysisCaption } from './labels'
import type { RequirementDto } from './types'

function RequirementList({ title, items }: { title: string; items: RequirementDto[] }) {
  return (
    <div className="grid content-start gap-2">
      <h4 className="text-sm font-semibold">{title}</h4>
      {items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Ninguno detectado.</p>
      ) : (
        <ul className="grid content-start gap-1 text-sm" aria-label={title}>
          {items.map((r, i) => (
            <li key={i} className="flex flex-wrap items-center gap-1.5">
              <span>{r.text}</span>
              {r.minYears != null && <span className="text-xs text-muted-foreground">({r.minYears}+ años)</span>}
              {r.extractedBy === 'DICTIONARY' && (
                <Badge variant="outline" className="h-4 px-1 text-[10px]" title="Detectado por diccionario, sin IA">
                  dicc.
                </Badge>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export function AnalysisCard({ jobId }: { jobId: number }) {
  const { data, isLoading } = useJobAnalysis(jobId)
  const analyze = useAnalyzeJob(jobId)
  const running = data?.task.state === 'RUNNING' || analyze.isPending
  const analysis = data?.analysis
  const output = analysis?.status === 'SUCCEEDED' ? analysis.output : null
  const requirements = data?.requirements ?? []

  const run = (force: boolean) =>
    analyze.mutate(force, {
      onError: (e) => {
        const code = apiErrorCode(e)
        toast.error(code ? taskErrorMessage(code, errorMessage(e)) : errorMessage(e))
      },
    })

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="flex items-center gap-2 text-base font-semibold">
              <Sparkles className="size-4 text-primary" aria-hidden /> Análisis con IA
            </h2>
            {analysis ? (
              <Button variant="outline" size="sm" onClick={() => run(true)} disabled={running}>
                {running ? <Loader2 className="animate-spin" aria-hidden /> : <RefreshCw aria-hidden />}
                Reanalizar
              </Button>
            ) : (
              <Button size="sm" onClick={() => run(false)} disabled={running || isLoading}>
                {running ? <Loader2 className="animate-spin" aria-hidden /> : <Sparkles aria-hidden />}
                Analizar oferta
              </Button>
            )}
          </div>
        </CardTitle>
        <CardDescription>Solo se envía el texto de la oferta; nunca tus datos personales. El resultado se guarda por oferta.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        {isLoading ? (
          <Skeleton className="h-24 w-full" />
        ) : (
          <>
            <TaskStatus task={data?.task} runningText="Analizando la oferta…" />
            {analysis && (
              <div className="flex flex-wrap gap-1.5">
                <StatusBadge tone="violet">{analysisCaption(analysis)}</StatusBadge>
                {analysis.status !== 'SUCCEEDED' && analysis.status !== 'RUNNING' && (
                  <StatusBadge tone="danger">{analysis.status === 'REFUSED' ? 'Rechazado por la IA' : analysis.status === 'INVALID_OUTPUT' ? 'Respuesta inválida' : 'Error'}</StatusBadge>
                )}
              </div>
            )}
            {output?.injectionSuspected && (
              <p role="alert" className="flex items-start gap-2 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm font-medium text-amber-950 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-100">
                <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
                La oferta contiene texto que intentó dar instrucciones a la IA; revisa con cuidado.
              </p>
            )}
            {output?.summary && <p className="text-sm">{output.summary}</p>}
            {output && (
              <dl className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">
                {output.seniority && (
                  <div>
                    <dt className="text-xs text-muted-foreground">Seniority</dt>
                    <dd>{output.seniority}</dd>
                  </div>
                )}
                {output.minYearsExperience != null && (
                  <div>
                    <dt className="text-xs text-muted-foreground">Experiencia mínima</dt>
                    <dd>{output.minYearsExperience} años</dd>
                  </div>
                )}
                {output.location?.evidenceQuote && (
                  <div className="col-span-2 sm:col-span-3">
                    <dt className="text-xs text-muted-foreground">Evidencia de ubicación (cita textual)</dt>
                    <dd className="italic">«{output.location.evidenceQuote}»</dd>
                  </div>
                )}
              </dl>
            )}
            {requirements.length > 0 && (
              <div className="grid gap-4 sm:grid-cols-2">
                <RequirementList title="Obligatorias" items={requirements.filter((r) => r.importance === 'MANDATORY')} />
                <RequirementList title="Deseables" items={requirements.filter((r) => r.importance === 'PREFERRED')} />
              </div>
            )}
            {output && output.languages.length > 0 && (
              <div className="grid gap-1">
                <h4 className="text-sm font-semibold">Idiomas</h4>
                <ul className="flex flex-wrap gap-1.5 text-sm">
                  {output.languages.map((l) => (
                    <li key={l.code}>
                      <Badge variant={l.mandatory ? 'default' : 'secondary'}>
                        {languageName(l.code)}
                        {l.level ? ` ${l.level}` : ''} · {l.mandatory ? 'obligatorio' : 'deseable'}
                      </Badge>
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {!analysis && !running && data?.task.state !== 'FAILED' && (
              <p className="text-sm text-muted-foreground">
                Extrae requisitos obligatorios y deseables, idiomas y condiciones de la oferta con el proveedor de IA configurado.
              </p>
            )}
          </>
        )}
      </CardContent>
    </Card>
  )
}
