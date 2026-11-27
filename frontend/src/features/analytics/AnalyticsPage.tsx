import { useState } from 'react'
import { BarChart3, Bot, Eye, Loader2, Sparkles } from 'lucide-react'
import { toast } from 'sonner'
import { DataTable } from '@/components/DataTable'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'
import { SelectField } from '@/components/SelectField'
import { StatusBadge } from '@/components/StatusBadge'
import { TaskStatus } from '@/components/TaskStatus'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/features/auth/auth-context'
import { classificationLabels } from '@/features/matching/labels'
import type { Classification } from '@/features/matching/types'
import { EvidenceBadge } from '@/features/profile/components/EvidenceBadge'
import { formatDateTime, formatUsd } from '@/lib/format'
import { regionLabels, toOptions } from '@/lib/labels'
import { errorMessage } from '@/lib/problem'
import { apiErrorCode, taskErrorMessage } from '@/lib/task'
import { useBreakdown, useDailyUsage, useFunnel, useGenerateSkillRecommendations, useOverview, useSkillDemand, useSkillRecommendations, useTimeline, type Period } from './api'
import { HBarChart, StackedDailyBars, TimelineChart } from './charts'
import { OTHER_COLOR, PERIOD_OPTIONS, SERIES_COLORS, dimensionLabels, funnelLabels, lastDays, percent, priorityLabels } from './labels'
import type { BreakdownDimension, DailyUsageRow } from './types'

function ChartCard({ title, description, children, actions }: { title: string; description?: string; children: React.ReactNode; actions?: React.ReactNode }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="text-base font-semibold">{title}</h2>
            {actions}
          </div>
        </CardTitle>
        {description && <CardDescription>{description}</CardDescription>}
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  )
}

function Kpi({ label, value, hint }: { label: string; value: string | number; hint?: string }) {
  return (
    <li className="grid gap-1 rounded-xl border bg-card p-4">
      <span className="text-2xl font-semibold tabular-nums">{value}</span>
      <span className="text-xs text-muted-foreground">{label}</span>
      {hint && <span className="text-[11px] text-muted-foreground">{hint}</span>}
    </li>
  )
}

const NoData = () => <p className="text-sm text-muted-foreground">Sin datos en este periodo.</p>

function BreakdownCard({ period }: { period: Period }) {
  const [dimension, setDimension] = useState<BreakdownDimension>('technology')
  const { data, isLoading, error, refetch } = useBreakdown(dimension, period)
  return (
    <ChartCard
      title="Ofertas por categoría"
      description="Ofertas detectadas por primera vez en el periodo."
      actions={
        <div className="w-56">
          <SelectField aria-label="Dimensión" value={dimension} onChange={(v) => v && setDimension(v)} options={toOptions(dimensionLabels)} />
        </div>
      }
    >
      {isLoading ? (
        <Skeleton className="h-48 w-full" />
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : !data?.length ? (
        <NoData />
      ) : (
        <HBarChart data={data.map((r) => ({ label: r.label, value: r.count }))} caption={`Ofertas por ${dimensionLabels[dimension].toLowerCase()}`} valueLabel="Ofertas" />
      )}
    </ChartCard>
  )
}

function SkillsCard() {
  const [region, setRegion] = useState('')
  const days = 90
  const demand = useSkillDemand(region, days)
  const recs = useSkillRecommendations()
  const generate = useGenerateSkillRecommendations()
  const running = generate.isPending || recs.data?.task.state === 'RUNNING'
  const result = recs.data?.result

  return (
    <div className="grid gap-4 xl:grid-cols-[1fr_24rem]">
      <ChartCard
        title="Demanda de tecnologías"
        description={`Proporción de ofertas de los últimos ${days} días que mencionan cada tecnología, junto a tu nivel de evidencia.`}
        actions={
          <div className="flex items-center gap-2">
            <StatusBadge tone="info" icon={<Eye aria-hidden />}>
              Datos observados
            </StatusBadge>
            <div className="w-44">
              <SelectField aria-label="Región" value={region} onChange={setRegion} noneLabel="Todas las regiones" options={toOptions(regionLabels)} />
            </div>
          </div>
        }
      >
        {demand.isLoading ? (
          <Skeleton className="h-48 w-full" />
        ) : demand.error ? (
          <ErrorState error={demand.error} onRetry={() => void demand.refetch()} />
        ) : (
          <DataTable
            caption="Demanda de tecnologías (datos observados)"
            rows={demand.data ?? []}
            getRowId={(r) => r.skillId}
            empty={<NoData />}
            columns={[
              { id: 'name', header: 'Tecnología', cell: (r) => <span className="font-medium">{r.name}</span> },
              { id: 'jobs', header: 'Ofertas', cell: (r) => r.jobs, className: 'tabular-nums' },
              {
                id: 'share',
                header: '% de ofertas',
                cell: (r) => (
                  <div className="flex items-center gap-2">
                    <div className="h-1.5 w-20 overflow-hidden rounded-full bg-muted" aria-hidden>
                      <div className="h-full bg-[var(--chart-1)]" style={{ width: `${Math.min(100, r.share * 100)}%` }} />
                    </div>
                    <span className="tabular-nums">{percent(r.share)}</span>
                  </div>
                ),
              },
              { id: 'level', header: 'Tu nivel', cell: (r) => (r.userEvidence ? <EvidenceBadge level={r.userEvidence} /> : <span className="text-xs text-muted-foreground">No la tienes</span>) },
            ]}
          />
        )}
      </ChartCard>
      <Card className="border-violet-300 dark:border-violet-800">
        <CardHeader>
          <CardTitle>
            <h2 className="flex items-center gap-2 text-base font-semibold">
              <Bot className="size-4 text-violet-600" aria-hidden /> Recomendación generada por IA
            </h2>
          </CardTitle>
          <CardDescription>Interpretación de la IA a partir de la demanda observada y tu perfil verificado; no es un dato observado.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-3">
          <Button
            variant="outline"
            disabled={running}
            onClick={() =>
              generate.mutate(
                { region, days },
                {
                  onError: (e) => {
                    const code = apiErrorCode(e)
                    toast.error(code ? taskErrorMessage(code, errorMessage(e)) : errorMessage(e))
                  },
                },
              )
            }
          >
            {running ? <Loader2 className="animate-spin" aria-hidden /> : <Sparkles aria-hidden />}
            {result ? 'Generar de nuevo' : 'Generar recomendaciones'}
          </Button>
          <TaskStatus task={recs.data?.task} runningText="Generando recomendaciones…" />
          {result && (
            <>
              <ul className="grid gap-2" aria-label="Recomendaciones de la IA">
                {result.recommendations.map((r, i) => (
                  <li key={i} className="grid gap-1 rounded-lg border p-2 text-sm">
                    <span className="flex flex-wrap items-center gap-2 font-medium">
                      {r.skill}
                      <StatusBadge tone={r.priority === 'HIGH' ? 'danger' : r.priority === 'MEDIUM' ? 'warning' : 'neutral'}>{priorityLabels[r.priority]}</StatusBadge>
                    </span>
                    <span className="text-muted-foreground">{r.reason}</span>
                  </li>
                ))}
              </ul>
              <p className="text-xs text-muted-foreground">{result.disclaimer}</p>
              <p className="text-xs text-muted-foreground">
                Basado en {result.basedOnJobs} ofertas · {result.provider} {result.model} · {formatDateTime(result.generatedAt)}
              </p>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

/** Pivots daily usage rows into stacked-bar data; keeps the 4 costliest codes and folds the rest into "Otros". */
function pivotUsage(rows: DailyUsageRow[]) {
  const totals = new Map<string, number>()
  for (const r of rows) totals.set(r.code, (totals.get(r.code) ?? 0) + r.costUsd)
  const top = [...totals.entries()].sort((a, b) => b[1] - a[1]).slice(0, SERIES_COLORS.length).map(([c]) => c).sort()
  const hasOther = totals.size > top.length
  const byDate = new Map<string, Record<string, string | number>>()
  for (const r of rows) {
    const key = top.includes(r.code) ? r.code : 'OTROS'
    const row = byDate.get(r.date) ?? { date: r.date }
    row[key] = Number(row[key] ?? 0) + r.costUsd
    byDate.set(r.date, row)
  }
  const series = top.map((c, i) => ({ key: c, label: c, color: SERIES_COLORS[i] }))
  if (hasOther) series.push({ key: 'OTROS', label: 'Otros', color: OTHER_COLOR })
  return { data: [...byDate.values()].sort((a, b) => String(a.date).localeCompare(String(b.date))), series }
}

export function AnalyticsPage() {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const [days, setDays] = useState('30')
  const period = lastDays(Number(days))
  const overview = useOverview(period)
  const timeline = useTimeline(period)
  const eligibility = useBreakdown('eligibility', period, 4)
  const funnel = useFunnel(period)
  const usage = useDailyUsage(period, isAdmin)
  const o = overview.data

  return (
    <>
      <PageHeader
        title="Analítica"
        description="Métricas de tu búsqueda calculadas con los datos guardados en JobHunter."
        actions={
          <div className="grid w-48 gap-1.5">
            <Label htmlFor="analytics-period">Periodo</Label>
            <SelectField id="analytics-period" value={days} onChange={(v) => v && setDays(v)} options={PERIOD_OPTIONS} />
          </div>
        }
      />
      {overview.error ? (
        <ErrorState error={overview.error} onRetry={() => void overview.refetch()} />
      ) : (
        <div className="grid gap-4">
          <section aria-labelledby="kpi-title">
            <h2 id="kpi-title" className="sr-only">
              Indicadores del periodo
            </h2>
            {!o ? (
              <Skeleton className="h-24 w-full" />
            ) : (
              <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
                <Kpi label="Ofertas nuevas" value={o.jobs.newInPeriod} />
                <Kpi label="Accesibles o probables desde Bolivia" value={o.jobs.eligibleOrLikely} />
                <Kpi label="Remotas" value={o.jobs.remote} />
                <Kpi label="Freelance" value={o.jobs.freelance} />
                <Kpi label="Ofertas activas" value={o.jobs.active} />
                <Kpi label="Postulaciones enviadas" value={o.applications.applied} />
                <Kpi label="Respuestas" value={o.applications.responses} />
                <Kpi label="Entrevistas" value={o.applications.interviews} />
                <Kpi label="Ofertas de trabajo" value={o.applications.offers} />
                <Kpi label="Tasa de respuesta" value={percent(o.applications.responseRate)} hint="Respuestas / postulaciones enviadas" />
              </ul>
            )}
          </section>

          <ChartCard title="Ofertas nuevas por día" description="Total detectado y cuántas son accesibles o probablemente accesibles desde Bolivia.">
            {timeline.isLoading ? <Skeleton className="h-60 w-full" /> : !timeline.data?.length ? <NoData /> : <TimelineChart data={timeline.data} />}
          </ChartCard>

          <div className="grid gap-4 xl:grid-cols-2">
            <BreakdownCard period={period} />
            <div className="grid content-start gap-4">
              <ChartCard title="Accesibilidad desde Bolivia">
                {eligibility.isLoading ? (
                  <Skeleton className="h-32 w-full" />
                ) : !eligibility.data?.length ? (
                  <NoData />
                ) : (
                  <HBarChart data={eligibility.data.map((r) => ({ label: r.label, value: r.count }))} caption="Ofertas por accesibilidad desde Bolivia" valueLabel="Ofertas" />
                )}
              </ChartCard>
              <ChartCard title="Embudo de postulaciones">
                {funnel.isLoading ? (
                  <Skeleton className="h-40 w-full" />
                ) : !funnel.data?.length ? (
                  <NoData />
                ) : (
                  <HBarChart data={funnel.data.map((f) => ({ label: funnelLabels[f.stage] ?? f.stage, value: f.count }))} caption="Embudo de postulaciones" valueLabel="Postulaciones" />
                )}
              </ChartCard>
            </div>
          </div>

          {o && (
            <ChartCard title="Compatibilidad de las ofertas" description="Distribución de clasificaciones calculadas con tu perfil.">
              <HBarChart
                data={(Object.keys(classificationLabels) as Classification[]).map((c) => ({ label: classificationLabels[c], value: o.matches[c] ?? 0 }))}
                caption="Ofertas por clasificación de compatibilidad"
                valueLabel="Ofertas"
              />
            </ChartCard>
          )}

          <SkillsCard />

          {isAdmin && (
            <ChartCard
              title="Costo por día"
              description={o ? `Estimado del periodo: IA ${formatUsd(o.usage.aiCostUsd, 4)} · Apify ${formatUsd(o.usage.apifyCostUsd, 4)} · ${o.usage.totalRequests} peticiones. Los costos son estimaciones.` : 'Los costos son estimaciones.'}
              actions={<BarChart3 className="size-4 text-muted-foreground" aria-hidden />}
            >
              {usage.isLoading ? <Skeleton className="h-60 w-full" /> : !usage.data?.length ? <NoData /> : <StackedDailyBars {...pivotUsage(usage.data)} caption="Costo estimado por día e integración (USD)" />}
            </ChartCard>
          )}
        </div>
      )}
    </>
  )
}
