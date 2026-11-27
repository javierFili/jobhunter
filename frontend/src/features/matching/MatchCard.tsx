import { useState } from 'react'
import { Calculator, CircleHelp, Loader2, Sparkles } from 'lucide-react'
import { toast } from 'sonner'
import { SelectField } from '@/components/SelectField'
import { StatusBadge } from '@/components/StatusBadge'
import { TaskStatus } from '@/components/TaskStatus'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { profilesApi } from '@/features/profile/api'
import { formatRelative } from '@/lib/format'
import { errorMessage } from '@/lib/problem'
import { apiErrorCode, taskErrorMessage } from '@/lib/task'
import { cn } from '@/lib/utils'
import { useComputeMatch, useJobMatch } from './api'
import {
  DIMENSIONS,
  MATCH_DISCLAIMER,
  classificationLabels,
  classificationTone,
  dimensionLabels,
  dimensionWeights,
  methodLabels,
  recommendationLabels,
} from './labels'
import type { MatchDto, ScoreDimension } from './types'

const barColor = (score: number) => (score >= 70 ? 'bg-emerald-600' : score >= 40 ? 'bg-amber-500' : 'bg-red-500')

function reasonFor(match: MatchDto, dim: ScoreDimension): string | null {
  return match.reasons.find((r) => r.name === dim)?.reason ?? null
}

function ScoreBars({ match }: { match: MatchDto }) {
  return (
    <ul className="grid gap-3" aria-label="Puntuación por dimensión">
      {DIMENSIONS.map((dim) => {
        const score = match.scores[dim]
        const reason = reasonFor(match, dim)
        return (
          <li key={dim} className="grid gap-1">
            <div className="flex items-center justify-between text-sm">
              <span className="font-medium">
                {dimensionLabels[dim]} <span className="text-xs font-normal text-muted-foreground">(peso {dimensionWeights[dim]})</span>
              </span>
              <span className="tabular-nums">{score == null ? 'Sin datos' : `${Math.round(score)}/100`}</span>
            </div>
            <div
              className="h-2 overflow-hidden rounded-full bg-muted"
              role="meter"
              aria-label={dimensionLabels[dim]}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={score ?? undefined}
              aria-valuetext={score == null ? 'Sin datos' : `${Math.round(score)} de 100`}
            >
              {score != null && <div className={cn('h-full rounded-full', barColor(score))} style={{ width: `${Math.max(2, Math.min(100, score))}%` }} />}
            </div>
            {reason && <p className="text-xs text-muted-foreground">{reason}</p>}
          </li>
        )
      })}
    </ul>
  )
}

export function MatchView({ match }: { match: MatchDto }) {
  const aiReason = match.method === 'AI_ASSISTED' ? match.reasons.find((r) => r.name === 'ai') : undefined
  return (
    <div className="grid gap-5">
      <div className="flex flex-wrap items-center gap-4">
        <div className="flex items-baseline gap-1">
          <span className="text-5xl font-semibold tabular-nums" aria-label={`Puntuación total ${Math.round(match.scoreTotal)} de 100`}>
            {Math.round(match.scoreTotal)}
          </span>
          <span className="text-sm text-muted-foreground" aria-hidden>
            /100
          </span>
        </div>
        <div className="grid gap-1.5">
          <div className="flex flex-wrap gap-1.5">
            <StatusBadge tone={classificationTone[match.classification]} className="h-6 px-2.5 text-[0.8rem] font-semibold">
              {classificationLabels[match.classification]}
            </StatusBadge>
            <StatusBadge tone={match.method === 'AI_ASSISTED' ? 'violet' : 'neutral'}>{methodLabels[match.method]}</StatusBadge>
          </div>
          <p className="text-sm">
            Recomendación: <strong>{recommendationLabels[match.recommendation]}</strong>
          </p>
        </div>
        <Tooltip>
          <TooltipTrigger asChild>
            <button type="button" className="ml-auto inline-flex items-center gap-1 rounded text-xs text-muted-foreground underline-offset-2 hover:underline">
              <CircleHelp className="size-3.5" aria-hidden /> ¿Cómo se calcula?
            </button>
          </TooltipTrigger>
          <TooltipContent className="max-w-xs">
            Pesos: habilidades 30, experiencia 20, ubicación 15, idioma 10, seniority 10, compensación 10, contrato 5.
          </TooltipContent>
        </Tooltip>
      </div>
      <ScoreBars match={match} />
      {aiReason && (
        <div className="rounded-lg border border-violet-200 bg-violet-50 px-3 py-2 text-sm text-violet-950 dark:border-violet-800 dark:bg-violet-950 dark:text-violet-100">
          <p className="mb-0.5 text-xs font-semibold">Evaluación de la IA</p>
          <p>{aiReason.reason}</p>
        </div>
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <h4 className="mb-1.5 text-sm font-semibold">Fortalezas</h4>
          {match.strengths.length ? (
            <ul className="grid gap-1" aria-label="Fortalezas">
              {match.strengths.map((s, i) => (
                <li key={i} className="rounded-md bg-emerald-50 px-2 py-1 text-sm text-emerald-950 dark:bg-emerald-950 dark:text-emerald-100">
                  {s}
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-muted-foreground">—</p>
          )}
        </div>
        <div>
          <h4 className="mb-1.5 text-sm font-semibold">Brechas</h4>
          {match.gaps.length ? (
            <ul className="grid gap-1" aria-label="Brechas">
              {match.gaps.map((g, i) => (
                <li key={i} className="rounded-md bg-amber-50 px-2 py-1 text-sm text-amber-950 dark:bg-amber-950 dark:text-amber-100">
                  {g}
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-muted-foreground">—</p>
          )}
        </div>
      </div>
      <p className="text-xs text-muted-foreground">Actualizado {formatRelative(match.updatedAt)}</p>
    </div>
  )
}

export function MatchCard({ jobId }: { jobId: number }) {
  const [profileId, setProfileId] = useState<number | null>(null)
  const profiles = profilesApi.useList()
  const { data, isLoading } = useJobMatch(jobId, profileId)
  const compute = useComputeMatch(jobId, profileId)
  const running = data?.task.state === 'RUNNING'

  const run = (ai: boolean) =>
    compute.mutate(ai, {
      onSuccess: (r) => !r.ai && toast.success('Compatibilidad calculada'),
      onError: (e) => {
        const code = apiErrorCode(e)
        toast.error(code ? taskErrorMessage(code, errorMessage(e)) : errorMessage(e))
      },
    })

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h2 className="text-base font-semibold">Compatibilidad</h2>
        </CardTitle>
        <CardDescription>{MATCH_DISCLAIMER}</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        <div className="flex flex-wrap items-end gap-2">
          <div className="grid min-w-48 flex-1 gap-1.5">
            <Label htmlFor={`match-profile-${jobId}`}>Perfil profesional</Label>
            <SelectField
              id={`match-profile-${jobId}`}
              value={profileId != null ? String(profileId) : ''}
              onChange={(v) => setProfileId(v ? Number(v) : null)}
              noneLabel="Perfil predeterminado"
              options={(profiles.data ?? []).map((p) => ({ value: String(p.id), label: p.name }))}
            />
          </div>
          <Button variant="outline" onClick={() => run(false)} disabled={compute.isPending || running}>
            {compute.isPending && compute.variables === false ? <Loader2 className="animate-spin" aria-hidden /> : <Calculator aria-hidden />}
            Calcular
          </Button>
          <Button onClick={() => run(true)} disabled={compute.isPending || running}>
            {running || (compute.isPending && compute.variables === true) ? <Loader2 className="animate-spin" aria-hidden /> : <Sparkles aria-hidden />}
            Evaluar con IA
          </Button>
        </div>
        <p className="text-xs text-muted-foreground">«Calcular» es instantáneo y gratuito. «Evaluar con IA» envía tu perfil verificado (sin datos de contacto) al proveedor configurado y tiene costo.</p>
        {isLoading ? (
          <Skeleton className="h-40 w-full" />
        ) : (
          <>
            <TaskStatus task={data?.task} runningText="Evaluando la compatibilidad con IA…" />
            {data?.match ? <MatchView match={data.match} /> : !running && <p className="text-sm text-muted-foreground">Aún no se calculó la compatibilidad con este perfil.</p>}
          </>
        )}
      </CardContent>
    </Card>
  )
}
