import { useState } from 'react'
import { toast } from 'sonner'
import { DataTable } from '@/components/DataTable'
import { ErrorState } from '@/components/ErrorState'
import { ExternalLink } from '@/components/ExternalLink'
import { LoadingState } from '@/components/LoadingState'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { formatDateTime } from '@/lib/format'
import { errorMessage } from '@/lib/problem'
import { useAdminSources, useUpdateAdminSource } from './api'
import type { AdminSourceDto } from './types'

function LimitsCell({ source }: { source: AdminSourceDto }) {
  const update = useUpdateAdminSource()
  const [minInterval, setMinInterval] = useState(source.minIntervalMinutes?.toString() ?? '')
  const [cap, setCap] = useState(source.dailyRequestCap?.toString() ?? '')
  const valid = /^\d*$/.test(minInterval) && /^\d*$/.test(cap)
  return (
    <div className="flex flex-wrap items-end gap-2">
      <label className="grid gap-1 text-xs">
        Intervalo mín. (min)
        <Input className="h-8 w-24" inputMode="numeric" value={minInterval} onChange={(e) => setMinInterval(e.target.value)} aria-invalid={!/^\d*$/.test(minInterval) || undefined} />
      </label>
      <label className="grid gap-1 text-xs">
        Peticiones/día
        <Input className="h-8 w-24" inputMode="numeric" value={cap} onChange={(e) => setCap(e.target.value)} aria-invalid={!/^\d*$/.test(cap) || undefined} />
      </label>
      <Button
        size="sm"
        variant="outline"
        disabled={!valid || update.isPending}
        aria-label={`Guardar límites de ${source.displayName}`}
        onClick={() =>
          update.mutate(
            { code: source.code, body: { minIntervalMinutes: minInterval ? Number(minInterval) : null, dailyRequestCap: cap ? Number(cap) : null } },
            { onSuccess: () => toast.success('Límites de la fuente guardados'), onError: (e) => toast.error(errorMessage(e)) },
          )
        }
      >
        Guardar
      </Button>
    </div>
  )
}

export function AdminSourcesTab() {
  const { data, isLoading, error, refetch } = useAdminSources()
  const update = useUpdateAdminSource()
  if (isLoading) return <LoadingState />
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />
  return (
    <DataTable
      caption="Fuentes de empleo"
      rows={data ?? []}
      getRowId={(s) => s.code}
      columns={[
        {
          id: 'name',
          header: 'Fuente',
          cell: (s) => (
            <div className="grid gap-1">
              <span className="font-medium">{s.displayName}</span>
              <span className="text-xs text-muted-foreground">{s.type}</span>
              {s.termsUrl && (
                <ExternalLink href={s.termsUrl} className="text-xs text-primary underline-offset-2 hover:underline">
                  Términos de uso
                </ExternalLink>
              )}
            </div>
          ),
        },
        {
          id: 'terms',
          header: 'Condiciones',
          cell: (s) => (
            <div className="grid max-w-xs gap-1 text-xs">
              <div className="flex flex-wrap gap-1">
                {s.attributionRequired && <StatusBadge tone="info">Atribución obligatoria</StatusBadge>}
                {s.commercialUseAllowed === false && <StatusBadge tone="warning">Sin uso comercial</StatusBadge>}
              </div>
              {s.attributionText && <span>Atribución: «{s.attributionText}»</span>}
              {s.termsNotes && <span className="text-muted-foreground">{s.termsNotes}</span>}
            </div>
          ),
        },
        { id: 'limits', header: 'Límites', cell: (s) => <LimitsCell key={`${s.minIntervalMinutes}-${s.dailyRequestCap}`} source={s} /> },
        { id: 'last', header: 'Última consulta', cell: (s) => (s.lastFetchAt ? formatDateTime(s.lastFetchAt) : 'Nunca'), className: 'whitespace-nowrap' },
        {
          id: 'enabled',
          header: 'Activa',
          cell: (s) => (
            <Switch
              checked={s.enabled}
              aria-label={`Activar fuente ${s.displayName}`}
              onCheckedChange={(enabled) => update.mutate({ code: s.code, body: { enabled } }, { onError: (e) => toast.error(errorMessage(e)) })}
            />
          ),
        },
      ]}
    />
  )
}
