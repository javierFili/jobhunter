import { useState } from 'react'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { SelectField } from '@/components/SelectField'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'
import { useCompare } from '../api'
import { diffSectionLabels } from '../labels'
import type { DiffLine, DiffSection, VersionSummaryDto } from '../types'

const lineClass: Record<DiffLine['op'], string> = {
  SAME: 'text-foreground',
  ADDED: 'bg-emerald-50 text-emerald-950 dark:bg-emerald-950/70 dark:text-emerald-100',
  REMOVED: 'bg-red-50 text-red-950 line-through decoration-red-400/70 dark:bg-red-950/70 dark:text-red-100',
}
const prefix: Record<DiffLine['op'], string> = { SAME: ' ', ADDED: '+', REMOVED: '−' }
const srOp: Record<DiffLine['op'], string> = { SAME: '', ADDED: 'Agregado: ', REMOVED: 'Eliminado: ' }

function groupBySection(lines: DiffLine[]): [DiffSection, DiffLine[]][] {
  const groups = new Map<DiffSection, DiffLine[]>()
  for (const l of lines) {
    const g = groups.get(l.section) ?? []
    g.push(l)
    groups.set(l.section, g)
  }
  return [...groups.entries()]
}

function Lines({ lines }: { lines: DiffLine[] }) {
  return (
    <ul className="grid gap-px font-mono text-xs">
      {lines.map((l, i) => (
        <li key={i} data-op={l.op} className={cn('flex gap-2 rounded px-2 py-1 whitespace-pre-wrap', lineClass[l.op])}>
          <span aria-hidden className="w-3 shrink-0 select-none">
            {prefix[l.op]}
          </span>
          <span>
            <span className="sr-only">{srOp[l.op]}</span>
            {l.text}
          </span>
        </li>
      ))}
    </ul>
  )
}

export function CompareView({
  resumeId,
  versions,
  initialTo = null,
  initialFromId = null,
}: {
  resumeId: number
  versions: VersionSummaryDto[]
  /** Version number to compare against (e.g. a freshly optimized version). */
  initialTo?: number | null
  /** Version id (not number) of the base version, e.g. VersionSummaryDto.parentVersionId. */
  initialFromId?: number | null
}) {
  const sorted = [...versions].sort((a, b) => a.versionNo - b.versionNo)
  const defaultTo = initialTo ?? sorted[sorted.length - 1]?.versionNo ?? null
  const parent = initialFromId != null ? sorted.find((v) => v.id === initialFromId)?.versionNo : undefined
  const defaultFrom = parent ?? (defaultTo != null ? sorted.filter((v) => v.versionNo < defaultTo).pop()?.versionNo : undefined) ?? null
  const [from, setFrom] = useState<string>(defaultFrom != null ? String(defaultFrom) : '')
  const [to, setTo] = useState<string>(defaultTo != null ? String(defaultTo) : '')
  const [mode, setMode] = useState<'unified' | 'split'>('unified')
  const fromNo = from ? Number(from) : null
  const toNo = to ? Number(to) : null
  const { data, isLoading, error, refetch } = useCompare(resumeId, fromNo, toNo)
  const options = sorted.map((v) => ({ value: String(v.versionNo), label: `v${v.versionNo}` }))

  if (versions.length < 2) return <p className="text-sm text-muted-foreground">Necesitas al menos dos versiones para comparar.</p>

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-end gap-3">
        <div className="grid w-32 gap-1.5">
          <Label htmlFor="cmp-from">Desde</Label>
          <SelectField id="cmp-from" value={from} onChange={setFrom} options={options} />
        </div>
        <div className="grid w-32 gap-1.5">
          <Label htmlFor="cmp-to">Hasta</Label>
          <SelectField id="cmp-to" value={to} onChange={setTo} options={options} />
        </div>
        <div role="group" aria-label="Modo de comparación" className="flex gap-1">
          <Button type="button" variant={mode === 'unified' ? 'secondary' : 'ghost'} size="sm" aria-pressed={mode === 'unified'} onClick={() => setMode('unified')}>
            Unificado
          </Button>
          <Button type="button" variant={mode === 'split' ? 'secondary' : 'ghost'} size="sm" aria-pressed={mode === 'split'} onClick={() => setMode('split')}>
            Lado a lado
          </Button>
        </div>
      </div>
      {fromNo === toNo ? (
        <p className="text-sm text-muted-foreground">Elige dos versiones distintas.</p>
      ) : isLoading ? (
        <LoadingState rows={2} />
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : !data?.some((l) => l.op !== 'SAME') ? (
        <p className="text-sm text-muted-foreground">No hay diferencias entre estas versiones.</p>
      ) : (
        <div className="grid gap-4">
          {groupBySection(data).map(([section, lines]) => (
            <section key={section} aria-label={`Cambios en ${diffSectionLabels[section]}`} className="grid gap-2 rounded-xl border p-3">
              <h3 className="text-sm font-semibold">{diffSectionLabels[section]}</h3>
              {mode === 'unified' ? (
                <Lines lines={lines} />
              ) : (
                <div className="grid gap-3 md:grid-cols-2">
                  <div>
                    <p className="mb-1 text-xs text-muted-foreground">v{from}</p>
                    <Lines lines={lines.filter((l) => l.op !== 'ADDED')} />
                  </div>
                  <div>
                    <p className="mb-1 text-xs text-muted-foreground">v{to}</p>
                    <Lines lines={lines.filter((l) => l.op !== 'REMOVED')} />
                  </div>
                </div>
              )}
            </section>
          ))}
        </div>
      )}
    </div>
  )
}
