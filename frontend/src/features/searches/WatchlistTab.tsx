import { useState, type FormEvent } from 'react'
import { Building2, CheckCircle2, Loader2, Plus, Trash2, XCircle } from 'lucide-react'
import { toast } from 'sonner'
import { DataTable } from '@/components/DataTable'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { FormField } from '@/components/FormField'
import { LoadingState } from '@/components/LoadingState'
import { SelectField } from '@/components/SelectField'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { formatDateTime } from '@/lib/format'
import { toOptions } from '@/lib/labels'
import { ApiError, errorMessage } from '@/lib/problem'
import { useAddWatch, useDeleteWatch, useUpdateWatch, useValidateWatch, useWatchlist } from './api'
import { BOARD_TOKEN_RE, atsBoardUrl, atsLabels } from './labels'
import type { Ats } from './types'

function AddWatchForm() {
  const [ats, setAts] = useState<Ats>('GREENHOUSE')
  const [token, setToken] = useState('')
  const [company, setCompany] = useState('')
  const [error, setError] = useState<string | null>(null)
  const validate = useValidateWatch()
  const add = useAddWatch()

  const check = (): string | null => {
    const t = token.trim()
    if (!t) return 'Ingresa el identificador del tablero'
    if (!BOARD_TOKEN_RE.test(t)) return 'Solo letras, números, puntos, guiones y guiones bajos (máx. 100). Usa solo el último segmento de la URL.'
    return null
  }

  const onValidate = () => {
    const err = check()
    setError(err)
    if (err) return
    validate.mutate({ ats, boardToken: token.trim() }, { onError: (e) => toast.error(errorMessage(e)) })
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    const err = check()
    setError(err)
    if (err) return
    add.mutate(
      { ats, boardToken: token.trim(), companyName: company.trim() || null },
      {
        onSuccess: () => {
          toast.success('Empresa agregada a la lista de vigilancia')
          setToken('')
          setCompany('')
          validate.reset()
        },
        onError: (e) => {
          if (e instanceof ApiError && e.code === 'INVALID_TOKEN') setError('El identificador no es válido.')
          toast.error(errorMessage(e))
        },
      },
    )
  }

  const result = validate.data
  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h2 className="text-base font-semibold">Agregar empresa</h2>
        </CardTitle>
        <CardDescription>
          Greenhouse, Lever y Ashby no tienen búsqueda global: hay que indicar el tablero de cada empresa. El identificador
          es el último segmento de la URL: {Object.values(atsBoardUrl).map((u, i) => (
            <span key={u}>
              {i > 0 && ', '}
              <code className="rounded bg-muted px-1">{u}&lt;token&gt;</code>
            </span>
          ))}
          .
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} noValidate className="grid gap-4">
          <div className="grid gap-4 sm:grid-cols-3">
            <FormField id="wl-ats" label="Plataforma">
              <SelectField value={ats} onChange={(v) => setAts((v || 'GREENHOUSE') as Ats)} options={toOptions(atsLabels)} />
            </FormField>
            <FormField id="wl-token" label="Identificador del tablero" required error={error ?? undefined} hint={`Ej.: ${atsBoardUrl[ats]}acme → acme`}>
              <Input value={token} onChange={(e) => setToken(e.target.value)} autoComplete="off" />
            </FormField>
            <FormField id="wl-company" label="Nombre de la empresa">
              <Input value={company} onChange={(e) => setCompany(e.target.value)} />
            </FormField>
          </div>
          {result && (
            <p role="status" className={result.ok ? 'flex items-center gap-1.5 text-sm text-emerald-700 dark:text-emerald-400' : 'flex items-center gap-1.5 text-sm text-destructive'}>
              {result.ok ? <CheckCircle2 className="size-4" aria-hidden /> : <XCircle className="size-4" aria-hidden />}
              {result.ok ? `Tablero válido: ${result.jobCount} ofertas publicadas.` : result.message}
            </p>
          )}
          <div className="flex flex-wrap justify-end gap-2">
            <Button type="button" variant="outline" onClick={onValidate} disabled={validate.isPending}>
              {validate.isPending && <Loader2 className="animate-spin" aria-hidden />}
              Validar
            </Button>
            <Button type="submit" disabled={add.isPending}>
              <Plus aria-hidden /> Agregar
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}

export function WatchlistTab() {
  const { data, isLoading, error, refetch } = useWatchlist()
  const update = useUpdateWatch()
  const remove = useDeleteWatch()
  return (
    <div className="grid gap-4">
      <AddWatchForm />
      {isLoading ? (
        <LoadingState rows={2} />
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <DataTable
          caption="Empresas vigiladas"
          rows={data ?? []}
          getRowId={(w) => w.id}
          empty={<EmptyState icon={Building2} title="Aún no vigilas ninguna empresa" description="Agrega el tablero de empleos de las empresas que te interesan." />}
          columns={[
            {
              id: 'company',
              header: 'Empresa',
              cell: (w) => (
                <div>
                  <div className="font-medium">{w.companyName ?? w.boardToken}</div>
                  <div className="text-xs text-muted-foreground">
                    {atsLabels[w.ats]} · {w.boardToken}
                  </div>
                </div>
              ),
            },
            { id: 'jobs', header: 'Ofertas', cell: (w) => (w.lastJobCount != null ? w.lastJobCount : '—') },
            {
              id: 'checked',
              header: 'Última revisión',
              cell: (w) => (
                <div className="flex flex-wrap items-center gap-1.5">
                  <span>{w.lastCheckedAt ? formatDateTime(w.lastCheckedAt) : 'Nunca'}</span>
                  {w.lastStatus === 'OK' && <StatusBadge tone="success">Correcta</StatusBadge>}
                  {w.lastStatus === 'ERROR' && <StatusBadge tone="danger">Error</StatusBadge>}
                </div>
              ),
            },
            {
              id: 'enabled',
              header: 'Activa',
              cell: (w) => (
                <Switch
                  checked={w.enabled}
                  aria-label={`Vigilar ${w.companyName ?? w.boardToken}`}
                  onCheckedChange={(enabled) => update.mutate({ id: w.id, enabled }, { onError: (e) => toast.error(errorMessage(e)) })}
                />
              ),
            },
            {
              id: 'actions',
              header: <span className="sr-only">Acciones</span>,
              cell: (w) => (
                <Button
                  variant="ghost"
                  size="icon-sm"
                  className="text-destructive"
                  aria-label={`Dejar de vigilar ${w.companyName ?? w.boardToken}`}
                  onClick={() => remove.mutate(w.id, { onSuccess: () => toast.success('Empresa eliminada de la lista'), onError: (e) => toast.error(errorMessage(e)) })}
                >
                  <Trash2 aria-hidden />
                </Button>
              ),
            },
          ]}
        />
      )}
    </div>
  )
}
