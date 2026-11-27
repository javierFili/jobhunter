import { useState } from 'react'
import { Info, Loader2, Plus, Save, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { formatDateTime } from '@/lib/format'
import { errorMessage } from '@/lib/problem'
import { useFxRates, useSaveFxRates } from './api'
import type { FxRateDto } from './types'

interface Row {
  key: number
  currency: string
  rate: string
  asOf: string | null
}

let seq = 0
const toRows = (list: FxRateDto[]): Row[] => list.map((r) => ({ key: ++seq, currency: r.currency, rate: String(r.rateToUsd), asOf: r.asOf }))

function FxEditor({ initial }: { initial: FxRateDto[] }) {
  const [rows, setRows] = useState<Row[]>(() => toRows(initial))
  const [showErrors, setShowErrors] = useState(false)
  const save = useSaveFxRates()
  const update = (key: number, patch: Partial<Row>) => setRows((rs) => rs.map((r) => (r.key === key ? { ...r, ...patch } : r)))
  const currencyError = (r: Row) =>
    !/^[A-Z]{3}$/.test(r.currency) ? 'Código ISO de 3 letras' : rows.filter((x) => x.currency === r.currency).length > 1 ? 'Moneda repetida' : null
  const rateError = (r: Row) => (!/^\d+(\.\d+)?$/.test(r.rate) || Number(r.rate) <= 0 ? 'Debe ser mayor a 0' : null)
  const invalid = rows.some((r) => currencyError(r) || rateError(r))

  const submit = () => {
    setShowErrors(true)
    if (invalid) return
    save.mutate(
      rows.map((r) => ({ currency: r.currency, rateToUsd: Number(r.rate) })),
      { onSuccess: () => toast.success('Tipos de cambio guardados'), onError: (e) => toast.error(errorMessage(e)) },
    )
  }

  return (
    <div className="grid gap-4">
      <p className="flex items-start gap-2 rounded-lg border border-sky-200 bg-sky-50 px-3 py-2 text-sm text-sky-900 dark:border-sky-800 dark:bg-sky-950 dark:text-sky-100">
        <Info className="mt-0.5 size-4 shrink-0" aria-hidden />
        Se usan solo para convertir salarios a USD por mes al filtrar ofertas. Se ingresan manualmente; no se consulta
        ninguna API externa de tipos de cambio. «1 unidad de la moneda = X USD».
      </p>
      <ul className="grid gap-2" aria-label="Tipos de cambio">
        {rows.map((r) => {
          const ce = showErrors ? currencyError(r) : null
          const re = showErrors ? rateError(r) : null
          return (
            <li key={r.key} className="grid items-start gap-2 sm:grid-cols-[8rem_12rem_1fr_auto]">
              <div className="grid gap-1">
                <label htmlFor={`fx-cur-${r.key}`} className="text-xs font-medium">
                  Moneda
                </label>
                <Input id={`fx-cur-${r.key}`} value={r.currency} maxLength={3} onChange={(e) => update(r.key, { currency: e.target.value.toUpperCase() })} aria-invalid={ce ? true : undefined} />
                {ce && <span className="text-xs text-destructive">{ce}</span>}
              </div>
              <div className="grid gap-1">
                <label htmlFor={`fx-rate-${r.key}`} className="text-xs font-medium">
                  Valor en USD
                </label>
                <Input id={`fx-rate-${r.key}`} inputMode="decimal" value={r.rate} onChange={(e) => update(r.key, { rate: e.target.value })} aria-invalid={re ? true : undefined} />
                {re && <span className="text-xs text-destructive">{re}</span>}
              </div>
              <span className="self-center text-xs text-muted-foreground">{r.asOf ? `Actualizado: ${formatDateTime(r.asOf)}` : 'Nuevo'}</span>
              <Button variant="ghost" size="icon" className="self-end text-destructive" aria-label={`Quitar ${r.currency || 'fila'}`} onClick={() => setRows((rs) => rs.filter((x) => x.key !== r.key))}>
                <Trash2 aria-hidden />
              </Button>
            </li>
          )
        })}
      </ul>
      <div className="flex flex-wrap justify-between gap-2">
        <Button variant="outline" onClick={() => setRows((rs) => [...rs, { key: ++seq, currency: '', rate: '', asOf: null }])}>
          <Plus aria-hidden /> Agregar moneda
        </Button>
        <Button onClick={submit} disabled={save.isPending}>
          {save.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <Save aria-hidden />}
          Guardar tipos de cambio
        </Button>
      </div>
    </div>
  )
}

export function FxRatesTab() {
  const { data, isLoading, error, refetch, dataUpdatedAt } = useFxRates()
  if (isLoading) return <LoadingState rows={2} />
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />
  return <FxEditor key={dataUpdatedAt} initial={data ?? []} />
}
