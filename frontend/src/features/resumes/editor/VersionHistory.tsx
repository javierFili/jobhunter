import { DataTable } from '@/components/DataTable'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { formatDateTime } from '@/lib/format'
import { resumeLanguageLabels, versionOriginLabels, versionOriginTone, versionStatusLabels, versionStatusTone } from '../labels'
import type { VersionSummaryDto } from '../types'

export function VersionHistory({
  versions,
  current,
  onOpen,
}: {
  versions: VersionSummaryDto[]
  current: number | null
  onOpen: (versionNo: number) => void
}) {
  return (
    <DataTable
      caption="Historial de versiones"
      rows={versions}
      getRowId={(v) => v.id}
      columns={[
        {
          id: 'no',
          header: 'Versión',
          cell: (v) => (
            <span className="font-medium tabular-nums">
              v{v.versionNo}
              {v.versionNo === current && <span className="ml-1 text-xs text-muted-foreground">(abierta)</span>}
            </span>
          ),
        },
        { id: 'origin', header: 'Origen', cell: (v) => <StatusBadge tone={versionOriginTone[v.origin]}>{versionOriginLabels[v.origin]}</StatusBadge> },
        {
          id: 'status',
          header: 'Estado',
          cell: (v) => (
            <div className="flex flex-wrap gap-1">
              <StatusBadge tone={versionStatusTone[v.status]}>{versionStatusLabels[v.status]}</StatusBadge>
              {v.hasPdf && <StatusBadge tone="teal">PDF aprobado</StatusBadge>}
            </div>
          ),
        },
        { id: 'lang', header: 'Idioma', cell: (v) => resumeLanguageLabels[v.language] },
        { id: 'created', header: 'Creada', cell: (v) => formatDateTime(v.createdAt), className: 'whitespace-nowrap' },
        { id: 'note', header: 'Nota', cell: (v) => <span className="line-clamp-2 text-sm">{v.note ?? '—'}</span> },
        {
          id: 'actions',
          header: <span className="sr-only">Acciones</span>,
          cell: (v) => (
            <Button variant="outline" size="sm" onClick={() => onOpen(v.versionNo)} aria-label={`Abrir versión ${v.versionNo}`}>
              Abrir
            </Button>
          ),
        },
      ]}
    />
  )
}
