import { DataTable } from '@/components/DataTable'
import { ErrorState } from '@/components/ErrorState'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatUsd } from '@/lib/format'
import { currentMonthRange } from './aiLabels'
import { useUsage } from './api'

const n = new Intl.NumberFormat('es')

/** "Consumo del mes": requests, estimated cost and AI tokens per integration. */
export function UsageCard({ compact = false }: { compact?: boolean }) {
  const { from, to } = currentMonthRange()
  const { data, isLoading, error, refetch } = useUsage(from, to)
  const rows = data ?? []
  const total = rows.reduce((a, r) => a + r.costUsd, 0)
  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h2 className="text-base font-semibold">Consumo del mes</h2>
        </CardTitle>
        <CardDescription>
          Costo estimado: <strong className="text-foreground">{formatUsd(total, 4)}</strong>. Los costos son estimaciones a partir de los tokens y
          tarifas configuradas; el monto real lo factura cada proveedor.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <Skeleton className="h-24 w-full" />
        ) : error ? (
          <ErrorState error={error} onRetry={() => void refetch()} />
        ) : rows.length === 0 ? (
          <p className="text-sm text-muted-foreground">Sin consumo registrado este mes.</p>
        ) : (
          <DataTable
            caption="Consumo del mes por integración"
            rows={rows}
            getRowId={(r) => r.code}
            columns={[
              { id: 'code', header: 'Integración', cell: (r) => <span className="font-medium">{r.code}</span> },
              { id: 'req', header: 'Peticiones', cell: (r) => n.format(r.requests), className: 'tabular-nums' },
              { id: 'cost', header: 'Costo estimado', cell: (r) => formatUsd(r.costUsd, 4), className: 'tabular-nums' },
              ...(compact
                ? []
                : [
                    {
                      id: 'tokens',
                      header: 'Tokens IA (entrada / salida / caché)',
                      cell: (r: (typeof rows)[number]) =>
                        r.inputTokens || r.outputTokens ? `${n.format(r.inputTokens)} / ${n.format(r.outputTokens)} / ${n.format(r.cachedTokens)}` : '—',
                      className: 'tabular-nums',
                    },
                  ]),
            ]}
          />
        )}
      </CardContent>
    </Card>
  )
}
