import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { Bookmark, CalendarClock, Loader2, Pencil, Play, Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { formatDateTime, formatRelative } from '@/lib/format'
import { errorMessage } from '@/lib/problem'
import { useDeleteSearch, useSavedSearches } from './api'
import { executionStatusLabels, executionStatusTone } from './labels'
import { scheduleText } from './scheduleText'
import { SearchFormDialog } from './SearchFormDialog'
import type { SavedSearchDto } from './types'
import { useRunSearchAction } from './useRunSearchAction'
import { WatchlistTab } from './WatchlistTab'

function SearchList() {
  const { data, isLoading, error, refetch } = useSavedSearches()
  const remove = useDeleteSearch()
  const run = useRunSearchAction()
  const [editing, setEditing] = useState<SavedSearchDto | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [deleting, setDeleting] = useState<SavedSearchDto | null>(null)

  const openNew = () => {
    setEditing(null)
    setFormOpen(true)
  }

  return (
    <>
      <div className="mb-4 flex items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">Las búsquedas programadas se ejecutan solas y te avisan de ofertas nuevas.</p>
        <Button onClick={openNew}>
          <Plus aria-hidden /> Nueva búsqueda
        </Button>
      </div>
      {isLoading ? (
        <LoadingState />
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : !data?.length ? (
        <EmptyState
          icon={Bookmark}
          title="Aún no tienes búsquedas guardadas"
          description="Empieza con una plantilla, por ejemplo «Backend LATAM»."
          action={<Button onClick={openNew}>Nueva búsqueda</Button>}
        />
      ) : (
        <ul className="grid gap-3 lg:grid-cols-2">
          {data.map((s) => (
            <li key={s.id}>
              <article aria-label={`Búsqueda ${s.name}`} className="grid h-full gap-3 rounded-xl border bg-card p-4">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="min-w-0">
                    <h3 className="font-semibold">
                      <Link to={`/searches/${s.id}`} className="hover:underline">
                        {s.name}
                      </Link>
                    </h3>
                    {s.keywords && <p className="text-sm text-muted-foreground">«{s.keywords}»</p>}
                  </div>
                  {s.enabled ? <StatusBadge tone="success">Activa</StatusBadge> : <StatusBadge>Pausada</StatusBadge>}
                </div>
                <p className="flex items-center gap-1.5 text-sm">
                  <CalendarClock className="size-4 text-muted-foreground" aria-hidden />
                  {scheduleText(s)}
                  {s.enabled && s.nextRunAt && <span className="text-muted-foreground">· próxima {formatRelative(s.nextRunAt)}</span>}
                </p>
                {s.lastExecution ? (
                  <div className="flex flex-wrap items-center gap-2 text-sm">
                    <StatusBadge tone={executionStatusTone[s.lastExecution.status]}>{executionStatusLabels[s.lastExecution.status]}</StatusBadge>
                    <span>
                      {s.lastExecution.jobsFound} encontradas · <strong>{s.lastExecution.jobsNew} nuevas</strong>
                    </span>
                    <span className="text-muted-foreground">· {formatDateTime(s.lastExecution.startedAt)}</span>
                  </div>
                ) : (
                  <p className="text-sm text-muted-foreground">Nunca ejecutada.</p>
                )}
                <div className="mt-auto flex flex-wrap gap-2 border-t pt-3">
                  <Button size="sm" onClick={() => run.start(s.id)} disabled={run.pending}>
                    {run.runningId === s.id ? <Loader2 className="animate-spin" aria-hidden /> : <Play aria-hidden />}
                    Ejecutar ahora
                  </Button>
                  <Button asChild size="sm" variant="outline">
                    <Link to={`/searches/${s.id}`}>Ver ejecuciones</Link>
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    aria-label={`Editar ${s.name}`}
                    onClick={() => {
                      setEditing(s)
                      setFormOpen(true)
                    }}
                  >
                    <Pencil aria-hidden />
                  </Button>
                  <Button size="sm" variant="ghost" className="text-destructive" aria-label={`Eliminar ${s.name}`} onClick={() => setDeleting(s)}>
                    <Trash2 aria-hidden />
                  </Button>
                </div>
              </article>
            </li>
          ))}
        </ul>
      )}
      <SearchFormDialog open={formOpen} onOpenChange={setFormOpen} search={editing} />
      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(o) => !o && setDeleting(null)}
        title="¿Eliminar esta búsqueda?"
        description={deleting ? `Se eliminará «${deleting.name}». Las ofertas ya encontradas se conservan.` : undefined}
        confirmLabel="Eliminar"
        destructive
        pending={remove.isPending}
        onConfirm={() =>
          deleting &&
          remove.mutate(deleting.id, {
            onSuccess: () => {
              toast.success('Búsqueda eliminada')
              setDeleting(null)
            },
            onError: (e) => toast.error(errorMessage(e)),
          })
        }
      />
    </>
  )
}

export function SearchesPage() {
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab') === 'watchlist' ? 'watchlist' : 'searches'
  return (
    <>
      <PageHeader title="Búsquedas guardadas" description="Automatiza la búsqueda en las fuentes de empleo y vigila empresas concretas." />
      <Tabs value={tab} onValueChange={(v) => setParams(v === 'watchlist' ? { tab: v } : {}, { replace: true })}>
        <TabsList>
          <TabsTrigger value="searches">Búsquedas</TabsTrigger>
          <TabsTrigger value="watchlist">Empresas vigiladas</TabsTrigger>
        </TabsList>
        <TabsContent value="searches" className="pt-4">
          <SearchList />
        </TabsContent>
        <TabsContent value="watchlist" className="pt-4">
          <WatchlistTab />
        </TabsContent>
      </Tabs>
    </>
  )
}
