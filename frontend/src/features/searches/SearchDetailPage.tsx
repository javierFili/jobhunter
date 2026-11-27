import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { ArrowLeft, History, Loader2, Pencil, Play } from 'lucide-react'
import { DataTable } from '@/components/DataTable'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { Pagination } from '@/components/Pagination'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { JobCard } from '@/features/jobs/JobCard'
import { formatDateTime, formatUsd } from '@/lib/format'
import { useExecution, useExecutionJobs, useExecutions, useSavedSearch } from './api'
import { executionStatusLabels, executionStatusTone, sourceRunLabels, sourceRunTone } from './labels'
import { scheduleText } from './scheduleText'
import { SearchFormDialog } from './SearchFormDialog'
import { useRunSearchAction } from './useRunSearchAction'

function ExecutionPanel({ executionId }: { executionId: number }) {
  const { data, isLoading, error, refetch } = useExecution(executionId)
  const [onlyNew, setOnlyNew] = useState(true)
  const [page, setPage] = useState(0)
  const finished = !!data && data.status !== 'RUNNING'
  const jobs = useExecutionJobs(executionId, onlyNew, page, finished)

  if (isLoading) return <LoadingState rows={2} />
  if (error || !data) return <ErrorState error={error} onRetry={() => void refetch()} />

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h2 className="flex flex-wrap items-center gap-2 text-base font-semibold">
            Ejecución #{data.id}
            <StatusBadge tone={executionStatusTone[data.status]} icon={data.status === 'RUNNING' ? <Loader2 className="animate-spin" aria-hidden /> : undefined}>
              {executionStatusLabels[data.status]}
            </StatusBadge>
          </h2>
        </CardTitle>
      </CardHeader>
      <CardContent className="grid gap-4">
        <p aria-live="polite" className="text-sm">
          {data.status === 'RUNNING'
            ? 'Buscando en las fuentes… esta vista se actualiza cada 3 segundos.'
            : `${data.jobsFound} ofertas encontradas · ${data.jobsNew} nuevas · ${data.jobsDuplicate} duplicadas`}
          {data.costUsd != null && data.costUsd > 0 && ` · costo ${formatUsd(data.costUsd, 4)}`}
        </p>
        {data.errorSummary && <p className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">{data.errorSummary}</p>}
        <DataTable
          caption="Resultado por fuente"
          rows={data.sources}
          getRowId={(s) => s.sourceCode}
          columns={[
            { id: 'source', header: 'Fuente', cell: (s) => <span className="font-medium">{s.sourceCode}</span> },
            { id: 'status', header: 'Estado', cell: (s) => <StatusBadge tone={sourceRunTone[s.status]}>{sourceRunLabels[s.status]}</StatusBadge> },
            { id: 'fetched', header: 'Obtenidas', cell: (s) => s.itemsFetched },
            { id: 'new', header: 'Nuevas', cell: (s) => s.itemsNew },
            { id: 'dup', header: 'Duplicadas', cell: (s) => s.itemsDuplicate },
            { id: 'req', header: 'Peticiones', cell: (s) => s.requestCount },
            { id: 'err', header: 'Error', cell: (s) => <span className="text-xs text-destructive">{s.errorMessage ?? ''}</span> },
          ]}
        />
        {finished && (
          <section aria-labelledby="exec-jobs" className="grid gap-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h3 id="exec-jobs" className="font-semibold">
                Ofertas de esta ejecución
              </h3>
              <div className="flex items-center gap-2">
                <Switch
                  id="exec-only-new"
                  checked={onlyNew}
                  onCheckedChange={(v) => {
                    setOnlyNew(v)
                    setPage(0)
                  }}
                />
                <Label htmlFor="exec-only-new" className="font-normal">
                  Solo nuevas
                </Label>
              </div>
            </div>
            {jobs.isLoading ? (
              <LoadingState rows={2} />
            ) : jobs.error ? (
              <ErrorState error={jobs.error} onRetry={() => void jobs.refetch()} />
            ) : !jobs.data?.content.length ? (
              <p className="text-sm text-muted-foreground">No hay ofertas {onlyNew ? 'nuevas ' : ''}en esta ejecución.</p>
            ) : (
              <>
                {jobs.data.content.map((j) => (
                  <JobCard key={j.id} job={j} />
                ))}
                <Pagination page={jobs.data.page} totalPages={jobs.data.totalPages} totalElements={jobs.data.totalElements} onPageChange={setPage} />
              </>
            )}
          </section>
        )}
      </CardContent>
    </Card>
  )
}

export function SearchDetailPage() {
  const id = Number(useParams().searchId)
  const [params, setParams] = useSearchParams()
  const executionId = params.get('execution') ? Number(params.get('execution')) : null
  const [page, setPage] = useState(0)
  const [editOpen, setEditOpen] = useState(false)
  const search = useSavedSearch(id)
  const executions = useExecutions(id, page)
  const run = useRunSearchAction()

  if (search.isLoading) return <LoadingState />
  if (search.error || !search.data) return <ErrorState error={search.error} onRetry={() => void search.refetch()} />
  const s = search.data

  return (
    <>
      <Button asChild variant="ghost" size="sm" className="mb-2 -ml-2">
        <Link to="/searches">
          <ArrowLeft aria-hidden /> Búsquedas guardadas
        </Link>
      </Button>
      <PageHeader
        title={s.name}
        description={scheduleText(s)}
        actions={
          <>
            <Button variant="outline" onClick={() => setEditOpen(true)}>
              <Pencil aria-hidden /> Editar
            </Button>
            <Button onClick={() => run.start(s.id)} disabled={run.pending}>
              {run.pending ? <Loader2 className="animate-spin" aria-hidden /> : <Play aria-hidden />}
              Ejecutar ahora
            </Button>
          </>
        }
      />
      <div className="grid gap-4">
        {executionId != null && <ExecutionPanel key={executionId} executionId={executionId} />}
        <section aria-labelledby="executions-title" className="grid gap-3">
          <h2 id="executions-title" className="text-base font-semibold">
            Historial de ejecuciones
          </h2>
          {executions.isLoading ? (
            <LoadingState rows={2} />
          ) : executions.error ? (
            <ErrorState error={executions.error} onRetry={() => void executions.refetch()} />
          ) : (
            <>
              <DataTable
                caption="Historial de ejecuciones"
                rows={executions.data?.content ?? []}
                getRowId={(e) => e.id}
                empty={<EmptyState icon={History} title="Esta búsqueda aún no se ejecutó" />}
                columns={[
                  { id: 'started', header: 'Inicio', cell: (e) => formatDateTime(e.startedAt), className: 'whitespace-nowrap' },
                  { id: 'trigger', header: 'Origen', cell: (e) => (e.trigger === 'MANUAL' ? 'Manual' : 'Programada') },
                  { id: 'status', header: 'Estado', cell: (e) => <StatusBadge tone={executionStatusTone[e.status]}>{executionStatusLabels[e.status]}</StatusBadge> },
                  { id: 'found', header: 'Encontradas', cell: (e) => e.jobsFound },
                  { id: 'new', header: 'Nuevas', cell: (e) => <strong>{e.jobsNew}</strong> },
                  { id: 'cost', header: 'Costo', cell: (e) => (e.costUsd ? formatUsd(e.costUsd, 4) : '—') },
                  {
                    id: 'actions',
                    header: <span className="sr-only">Acciones</span>,
                    cell: (e) => (
                      <Button
                        variant={e.id === executionId ? 'secondary' : 'outline'}
                        size="sm"
                        aria-label={`Ver detalle de la ejecución ${e.id}`}
                        onClick={() => setParams({ execution: String(e.id) }, { replace: true })}
                      >
                        Ver detalle
                      </Button>
                    ),
                  },
                ]}
              />
              {executions.data && (
                <Pagination page={executions.data.page} totalPages={executions.data.totalPages} onPageChange={setPage} label="Paginación de ejecuciones" />
              )}
            </>
          )}
        </section>
      </div>
      <SearchFormDialog open={editOpen} onOpenChange={setEditOpen} search={s} />
    </>
  )
}
