import { useState } from 'react'
import { Loader2, RefreshCw, Sparkles, Target } from 'lucide-react'
import { toast } from 'sonner'
import { ChipMultiSelect } from '@/components/ChipMultiSelect'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { Pagination } from '@/components/Pagination'
import { SelectField } from '@/components/SelectField'
import { TaskStatus } from '@/components/TaskStatus'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { JobCard } from '@/features/jobs/JobCard'
import { profilesApi } from '@/features/profile/api'
import { toOptions } from '@/lib/labels'
import { errorMessage } from '@/lib/problem'
import { apiErrorCode, taskErrorMessage } from '@/lib/task'
import { useAnalyzeTop, useAnalyzeTopStatus, useRecommended, useRecompute } from './api'
import { DEFAULT_RECOMMENDED, MATCH_DISCLAIMER, classificationLabels } from './labels'
import type { Classification } from './types'

const limitOptions = Array.from({ length: 20 }, (_, i) => ({ value: String(i + 1), label: String(i + 1) }))

export function RecommendedPage() {
  const [profileId, setProfileId] = useState<number | null>(null)
  const [classification, setClassification] = useState<Classification[]>(DEFAULT_RECOMMENDED)
  const [page, setPage] = useState(0)
  const [limit, setLimit] = useState('5')
  const profiles = profilesApi.useList()
  const { data, isLoading, error, refetch } = useRecommended({ classification, profileId, page, size: 20 })
  const recompute = useRecompute()
  const analyzeTop = useAnalyzeTop()
  const topStatus = useAnalyzeTopStatus()
  const topRunning = topStatus.data?.state === 'RUNNING' || analyzeTop.isPending

  const onError = (e: unknown) => {
    const code = apiErrorCode(e)
    toast.error(code ? taskErrorMessage(code, errorMessage(e)) : errorMessage(e))
  }

  return (
    <>
      <PageHeader title="Empleos recomendados" description={MATCH_DISCLAIMER} />
      <div className="grid gap-4">
        <Card>
          <CardContent className="grid gap-4">
            <div className="flex flex-wrap items-end gap-3">
              <div className="grid min-w-56 gap-1.5">
                <Label htmlFor="rec-profile">Perfil profesional</Label>
                <SelectField
                  id="rec-profile"
                  value={profileId != null ? String(profileId) : ''}
                  onChange={(v) => {
                    setProfileId(v ? Number(v) : null)
                    setPage(0)
                  }}
                  noneLabel="Perfil predeterminado"
                  options={(profiles.data ?? []).map((p) => ({ value: String(p.id), label: p.name }))}
                />
              </div>
              <Button
                variant="outline"
                disabled={recompute.isPending}
                onClick={() =>
                  recompute.mutate(profileId, {
                    onSuccess: (r) => toast.success(`Se recalculó la compatibilidad de ${r.computed} ofertas`),
                    onError,
                  })
                }
              >
                {recompute.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <RefreshCw aria-hidden />}
                Recalcular
              </Button>
            </div>
            <div className="grid gap-1.5">
              <span className="text-sm font-medium" id="rec-class-label">
                Clasificación
              </span>
              <ChipMultiSelect<Classification>
                label="Clasificación"
                options={toOptions(classificationLabels)}
                value={classification}
                onChange={(v) => {
                  setClassification(v)
                  setPage(0)
                }}
              />
            </div>
            <div className="grid gap-2 rounded-lg border border-violet-200 bg-violet-50/60 p-3 dark:border-violet-900 dark:bg-violet-950/40">
              <div className="flex flex-wrap items-end gap-3">
                <div className="grid w-28 gap-1.5">
                  <Label htmlFor="rec-limit">Cantidad</Label>
                  <SelectField id="rec-limit" value={limit} onChange={(v) => setLimit(v || '5')} options={limitOptions} />
                </div>
                <Button
                  disabled={topRunning}
                  onClick={() => analyzeTop.mutate({ limit: Number(limit), profileId }, { onSuccess: () => toast.success('Evaluación con IA iniciada'), onError })}
                >
                  {topRunning ? <Loader2 className="animate-spin" aria-hidden /> : <Sparkles aria-hidden />}
                  Evaluar las mejores con IA
                </Button>
              </div>
              <p className="text-xs text-muted-foreground">Cada evaluación usa el proveedor configurado y tiene costo. Se evalúan las ofertas con mejor puntuación determinista.</p>
              <TaskStatus task={topStatus.data} runningText="Evaluando las mejores ofertas con IA…" />
            </div>
          </CardContent>
        </Card>

        {isLoading ? (
          <LoadingState rows={3} />
        ) : error ? (
          <ErrorState error={error} onRetry={() => void refetch()} />
        ) : !data?.content.length ? (
          <EmptyState
            icon={Target}
            title="Todavía no hay recomendaciones"
            description="La compatibilidad se calcula automáticamente después de cada ejecución de tus búsquedas guardadas. También puedes usar «Recalcular»."
          />
        ) : (
          <>
            <div className="grid gap-3">
              {data.content.map((j) => (
                <JobCard key={j.id} job={j} />
              ))}
            </div>
            <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPageChange={setPage} label="Paginación de recomendaciones" />
          </>
        )}
      </div>
    </>
  )
}
