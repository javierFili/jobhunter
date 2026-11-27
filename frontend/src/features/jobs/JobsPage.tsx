import { useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import { ChevronDown, Search, SearchX, SlidersHorizontal } from 'lucide-react'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { Pagination } from '@/components/Pagination'
import { SearchableMultiSelect } from '@/components/SearchableMultiSelect'
import { SelectField } from '@/components/SelectField'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { toOptions } from '@/lib/labels'
import { useJobs, useSources } from './api'
import { FiltersFields } from './FiltersFields'
import { JobCard } from './JobCard'
import { emptyFilters, jobSortLabels } from './labels'
import { countActiveFilters, parseJobQuery, toSearchParams } from './query'
import type { JobQuery, JobSort } from './types'

function FiltersPanel({ query, onApply }: { query: JobQuery; onApply: (q: JobQuery) => void }) {
  const [draft, setDraft] = useState<JobQuery>(query)
  const sources = useSources()
  const active = countActiveFilters(query.filters) + (query.sources.length ? 1 : 0) + (query.company ? 1 : 0)

  const submit = (e: FormEvent) => {
    e.preventDefault()
    onApply({ ...draft, page: 0 })
  }

  return (
    <Card>
      <CardContent>
        <form onSubmit={submit} className="grid gap-4" role="search" aria-label="Buscar empleos">
          <div className="flex flex-col gap-2 sm:flex-row">
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
              <Input
                aria-label="Palabras clave"
                placeholder="Ej.: Laravel, Java, backend…"
                className="pl-8"
                value={draft.q}
                onChange={(e) => setDraft({ ...draft, q: e.target.value })}
              />
            </div>
            <div className="w-full sm:w-56">
              <SelectField
                aria-label="Ordenar por"
                value={draft.sort}
                onChange={(s) => {
                  const next = { ...draft, sort: (s || 'recent') as JobSort, page: 0 }
                  setDraft(next)
                  onApply(next)
                }}
                options={toOptions(jobSortLabels)}
              />
            </div>
            <Button type="submit">Buscar</Button>
          </div>
          <div className="flex flex-wrap items-center gap-4">
            <div className="flex items-center gap-2">
              <Switch
                id="jobs-saved"
                checked={draft.saved}
                onCheckedChange={(saved) => {
                  const next = { ...draft, saved, page: 0 }
                  setDraft(next)
                  onApply(next)
                }}
              />
              <Label htmlFor="jobs-saved" className="font-normal">
                Solo guardadas
              </Label>
            </div>
            <div className="flex items-center gap-2">
              <Switch
                id="jobs-hidden"
                checked={draft.includeHidden}
                onCheckedChange={(includeHidden) => {
                  const next = { ...draft, includeHidden, page: 0 }
                  setDraft(next)
                  onApply(next)
                }}
              />
              <Label htmlFor="jobs-hidden" className="font-normal">
                Incluir ocultas
              </Label>
            </div>
          </div>
          <Collapsible>
            <CollapsibleTrigger asChild>
              <Button type="button" variant="outline" size="sm" className="group">
                <SlidersHorizontal aria-hidden /> Más filtros
                {active > 0 && <Badge className="ml-1 h-5">{active}</Badge>}
                <ChevronDown className="transition-transform group-data-[state=open]:rotate-180" aria-hidden />
              </Button>
            </CollapsibleTrigger>
            <CollapsibleContent className="grid gap-5 pt-4">
              <FiltersFields idPrefix="jobs" value={draft.filters} onChange={(filters) => setDraft({ ...draft, filters })} />
              <div className="grid gap-5 md:grid-cols-2">
                <div className="grid gap-1.5">
                  <Label htmlFor="jobs-company">Empresa</Label>
                  <Input id="jobs-company" value={draft.company} onChange={(e) => setDraft({ ...draft, company: e.target.value })} />
                </div>
                <div className="grid gap-1.5">
                  <Label htmlFor="jobs-sources">Fuentes</Label>
                  <SearchableMultiSelect
                    id="jobs-sources"
                    label="Fuentes"
                    options={(sources.data ?? []).map((s) => ({ value: s.code, label: s.displayName }))}
                    value={draft.sources}
                    onChange={(src) => setDraft({ ...draft, sources: src })}
                  />
                </div>
              </div>
              <div className="flex flex-wrap justify-end gap-2">
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() => {
                    const cleared = { ...draft, filters: emptyFilters(), company: '', sources: [], page: 0 }
                    setDraft(cleared)
                    onApply(cleared)
                  }}
                >
                  Limpiar filtros
                </Button>
                <Button type="submit">Aplicar filtros</Button>
              </div>
            </CollapsibleContent>
          </Collapsible>
        </form>
      </CardContent>
    </Card>
  )
}

export function JobsPage() {
  const [params, setParams] = useSearchParams()
  const query = parseJobQuery(params)
  const { data, isLoading, isFetching, error, refetch } = useJobs(query)
  const apply = (q: JobQuery) => setParams(toSearchParams(q))

  return (
    <>
      <PageHeader
        title="Descubrir empleos"
        description="Ofertas recopiladas de fuentes directas. La accesibilidad desde Bolivia se estima a partir del texto de cada oferta."
      />
      <div className="grid gap-4">
        <FiltersPanel key={params.toString()} query={query} onApply={apply} />
        <div aria-live="polite" className="text-sm text-muted-foreground">
          {data && !isLoading && `${data.totalElements} ofertas encontradas`}
          {isFetching && !isLoading && ' · actualizando…'}
        </div>
        {isLoading ? (
          <LoadingState rows={4} />
        ) : error ? (
          <ErrorState error={error} onRetry={() => void refetch()} />
        ) : !data?.content.length ? (
          <EmptyState
            icon={SearchX}
            title="No hay ofertas con estos filtros"
            description="Prueba con menos filtros o crea una búsqueda guardada para traer ofertas nuevas de las fuentes."
          />
        ) : (
          <>
            <div className="grid gap-3">
              {data.content.map((job) => (
                <JobCard key={job.id} job={job} />
              ))}
            </div>
            <Pagination
              page={data.page}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              onPageChange={(page) => apply({ ...query, page })}
              label="Paginación de ofertas"
            />
          </>
        )}
      </div>
    </>
  )
}
