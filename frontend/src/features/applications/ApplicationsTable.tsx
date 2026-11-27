import { useState } from 'react'
import { Link } from 'react-router'
import { Briefcase, Search } from 'lucide-react'
import { ChipMultiSelect } from '@/components/ChipMultiSelect'
import { DataTable } from '@/components/DataTable'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { Pagination } from '@/components/Pagination'
import { SelectField } from '@/components/SelectField'
import { StatusBadge } from '@/components/StatusBadge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { MatchScoreChip } from '@/features/matching/MatchScoreChip'
import { formatDate, formatDateTime } from '@/lib/format'
import { toOptions } from '@/lib/labels'
import { useApplications, type AppListParams } from './api'
import { isOverdue, statusLabels, statusTone } from './labels'
import type { AppStatus } from './types'

const sortOptions = [
  { value: 'updated', label: 'Última actualización' },
  { value: 'applied', label: 'Fecha de envío' },
  { value: 'followUp', label: 'Próximo seguimiento' },
]

export function ApplicationsTable() {
  const [params, setParams] = useState<AppListParams>({ status: [], q: '', sort: 'updated', page: 0, size: 20 })
  const [q, setQ] = useState('')
  const { data, isLoading, error, refetch } = useApplications(params)
  return (
    <div className="grid gap-4">
      <div className="grid gap-3">
        <div className="flex flex-wrap items-end gap-3">
          <form
            role="search"
            className="relative min-w-56 flex-1"
            onSubmit={(e) => {
              e.preventDefault()
              setParams((p) => ({ ...p, q, page: 0 }))
            }}
          >
            <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
            <Input aria-label="Buscar por empresa o cargo" placeholder="Buscar por empresa o cargo…" className="pl-8" value={q} onChange={(e) => setQ(e.target.value)} />
          </form>
          <div className="grid w-56 gap-1.5">
            <Label htmlFor="apps-sort">Ordenar por</Label>
            <SelectField id="apps-sort" value={params.sort} onChange={(v) => setParams((p) => ({ ...p, sort: (v || 'updated') as AppListParams['sort'], page: 0 }))} options={sortOptions} />
          </div>
        </div>
        <ChipMultiSelect<AppStatus> label="Filtrar por estado" options={toOptions(statusLabels)} value={params.status} onChange={(status) => setParams((p) => ({ ...p, status, page: 0 }))} />
      </div>
      {isLoading ? (
        <LoadingState />
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <>
          <DataTable
            caption="Postulaciones"
            rows={data?.content ?? []}
            getRowId={(a) => a.id}
            empty={<EmptyState icon={Briefcase} title="No hay postulaciones con estos filtros" />}
            columns={[
              {
                id: 'position',
                header: 'Puesto',
                cell: (a) => (
                  <div>
                    <Link to={`/applications/${a.id}`} className="font-medium hover:underline">
                      {a.positionTitle}
                    </Link>
                    <div className="text-xs text-muted-foreground">{a.companyName}</div>
                  </div>
                ),
              },
              { id: 'status', header: 'Estado', cell: (a) => <StatusBadge tone={statusTone[a.status]}>{statusLabels[a.status]}</StatusBadge> },
              { id: 'match', header: 'Compatibilidad', cell: (a) => (a.match ? <MatchScoreChip score={a.match.scoreTotal} classification={a.match.classification} /> : '—') },
              { id: 'applied', header: 'Enviada', cell: (a) => (a.appliedAt ? formatDateTime(a.appliedAt) : '—'), className: 'whitespace-nowrap' },
              {
                id: 'follow',
                header: 'Seguimiento',
                className: 'whitespace-nowrap',
                cell: (a) => (a.followUpAt ? <StatusBadge tone={isOverdue(a.followUpAt) ? 'danger' : 'neutral'}>{formatDate(a.followUpAt)}</StatusBadge> : '—'),
              },
              { id: 'updated', header: 'Actualizada', cell: (a) => formatDateTime(a.updatedAt), className: 'whitespace-nowrap' },
            ]}
          />
          {data && <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPageChange={(page) => setParams((p) => ({ ...p, page }))} />}
        </>
      )}
    </div>
  )
}
