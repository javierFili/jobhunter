import { useState, type FormEvent } from 'react'
import { AlertTriangle, KeyRound, Loader2, PlugZap, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { FormField } from '@/components/FormField'
import { StatusBadge, type BadgeTone } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { formatDateTime, formatUsd } from '@/lib/format'
import { ApiError, errorMessage } from '@/lib/problem'
import { cn } from '@/lib/utils'
import { useDeleteSecret, useSaveSecret, useTestIntegration, useUpdateIntegration } from './api'
import type { IntegrationDto, IntegrationStatus } from './types'

const statusInfo: Record<IntegrationStatus, { label: string; tone: BadgeTone; dot: string }> = {
  OK: { label: 'Funcionando', tone: 'success', dot: 'bg-emerald-500' },
  ERROR: { label: 'Con errores', tone: 'danger', dot: 'bg-red-500' },
  DISABLED: { label: 'Desactivada', tone: 'neutral', dot: 'bg-zinc-400' },
  UNKNOWN: { label: 'Sin comprobar', tone: 'neutral', dot: 'bg-amber-400' },
}

const MASTER_KEY_MESSAGE =
  'El servidor no tiene configurada la variable de entorno JOBHUNTER_MASTER_KEY, necesaria para cifrar las credenciales. Defínela en el servidor y reinicia el backend antes de guardar credenciales.'

function CredentialsForm({ integration }: { integration: IntegrationDto }) {
  // Write-only: values are never prefilled and are cleared after a successful save.
  const [values, setValues] = useState<Record<string, string>>({})
  const [masterKeyMissing, setMasterKeyMissing] = useState(false)
  const save = useSaveSecret()
  const remove = useDeleteSecret()
  const editable = integration.credentials.filter((c) => !c.fromEnv)
  const anyConfigured = editable.some((c) => c.configured)

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const payload = Object.fromEntries(Object.entries(values).filter(([, v]) => v.trim() !== ''))
    if (Object.keys(payload).length === 0) {
      toast.error('Ingresa al menos un valor')
      return
    }
    save.mutate(
      { code: integration.code, values: payload },
      {
        onSuccess: () => {
          setValues({})
          setMasterKeyMissing(false)
          toast.success('Credenciales guardadas')
        },
        onError: (err) => {
          if (err instanceof ApiError && err.code === 'MASTER_KEY_MISSING') {
            setMasterKeyMissing(true)
            toast.error('Falta JOBHUNTER_MASTER_KEY en el servidor')
          } else toast.error(errorMessage(err))
        },
      },
    )
  }

  return (
    <form onSubmit={submit} className="grid gap-3" aria-label={`Credenciales de ${integration.displayName}`}>
      <h4 className="flex items-center gap-1.5 text-sm font-semibold">
        <KeyRound className="size-4" aria-hidden /> Credenciales
      </h4>
      {masterKeyMissing && (
        <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {MASTER_KEY_MESSAGE}
        </p>
      )}
      {integration.credentials.map((c) => {
        const id = `cred-${integration.code}-${c.name}`
        const state = c.fromEnv ? 'Definida por variable de entorno' : c.configured ? `Configurada ••••${c.last4 ?? ''}` : 'Sin configurar'
        return (
          <FormField key={c.name} id={id} label={c.label} required={c.required && !c.configured && !c.fromEnv} hint={state}>
            <Input
              type="password"
              autoComplete="new-password"
              spellCheck={false}
              disabled={c.fromEnv}
              placeholder={c.configured ? 'Escribe un valor nuevo para reemplazarla' : ''}
              value={values[c.name] ?? ''}
              onChange={(e) => setValues((v) => ({ ...v, [c.name]: e.target.value }))}
            />
          </FormField>
        )
      })}
      {editable.length > 0 && (
        <div className="flex flex-wrap justify-end gap-2">
          {anyConfigured && (
            <Button
              type="button"
              variant="ghost"
              className="text-destructive"
              disabled={remove.isPending}
              onClick={() =>
                remove.mutate(integration.code, {
                  onSuccess: () => toast.success('Credenciales eliminadas'),
                  onError: (e) => toast.error(errorMessage(e)),
                })
              }
            >
              <Trash2 aria-hidden /> Eliminar credenciales
            </Button>
          )}
          <Button type="submit" variant="outline" disabled={save.isPending}>
            {save.isPending && <Loader2 className="animate-spin" aria-hidden />}
            Guardar credenciales
          </Button>
        </div>
      )}
    </form>
  )
}

function LimitsForm({ integration }: { integration: IntegrationDto }) {
  const update = useUpdateIntegration()
  const [daily, setDaily] = useState(integration.dailyLimit?.toString() ?? '')
  const [monthly, setMonthly] = useState(integration.monthlyLimit?.toString() ?? '')
  const [budget, setBudget] = useState(integration.monthlyBudgetUsd?.toString() ?? '')
  const [config, setConfig] = useState(JSON.stringify(integration.config ?? {}, null, 2))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const p = `lim-${integration.code}`

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const errs: Record<string, string> = {}
    const int = (v: string, k: string) => {
      if (v.trim() === '') return null
      if (!/^\d+$/.test(v.trim())) errs[k] = 'Ingresa un número entero ≥ 0'
      return Number(v)
    }
    const dailyLimit = int(daily, 'daily')
    const monthlyLimit = int(monthly, 'monthly')
    let monthlyBudgetUsd: number | null = null
    if (budget.trim() !== '') {
      if (!/^\d+(\.\d{1,2})?$/.test(budget.trim())) errs.budget = 'Ingresa un monto válido'
      else monthlyBudgetUsd = Number(budget)
    }
    let parsedConfig: Record<string, unknown> = {}
    try {
      const c = JSON.parse(config || '{}') as unknown
      if (!c || typeof c !== 'object' || Array.isArray(c)) throw new Error()
      parsedConfig = c as Record<string, unknown>
    } catch {
      errs.config = 'Debe ser un objeto JSON válido'
    }
    setErrors(errs)
    if (Object.keys(errs).length) return
    update.mutate(
      { code: integration.code, body: { dailyLimit, monthlyLimit, monthlyBudgetUsd, config: parsedConfig, version: integration.version } },
      { onSuccess: () => toast.success('Límites guardados'), onError: (err) => toast.error(errorMessage(err)) },
    )
  }

  return (
    <form onSubmit={submit} noValidate className="grid gap-3" aria-label={`Límites de ${integration.displayName}`}>
      <h4 className="text-sm font-semibold">Límites y configuración</h4>
      <div className="grid gap-3 sm:grid-cols-3">
        <FormField id={`${p}-daily`} label="Peticiones por día" error={errors.daily}>
          <Input inputMode="numeric" value={daily} onChange={(e) => setDaily(e.target.value)} />
        </FormField>
        <FormField id={`${p}-monthly`} label="Peticiones por mes" error={errors.monthly}>
          <Input inputMode="numeric" value={monthly} onChange={(e) => setMonthly(e.target.value)} />
        </FormField>
        <FormField id={`${p}-budget`} label="Presupuesto mensual (USD)" error={errors.budget}>
          <Input inputMode="decimal" value={budget} onChange={(e) => setBudget(e.target.value)} />
        </FormField>
      </div>
      <Collapsible>
        <CollapsibleTrigger asChild>
          <Button type="button" variant="link" size="sm" className="px-0">
            Configuración avanzada (JSON)
          </Button>
        </CollapsibleTrigger>
        <CollapsibleContent>
          <FormField id={`${p}-config`} label="Configuración" error={errors.config}>
            <Textarea rows={5} className="font-mono text-xs" value={config} onChange={(e) => setConfig(e.target.value)} spellCheck={false} />
          </FormField>
        </CollapsibleContent>
      </Collapsible>
      <div className="flex justify-end">
        <Button type="submit" variant="outline" disabled={update.isPending}>
          {update.isPending && <Loader2 className="animate-spin" aria-hidden />}
          Guardar límites
        </Button>
      </div>
    </form>
  )
}

export function IntegrationCard({ integration }: { integration: IntegrationDto }) {
  const update = useUpdateIntegration()
  const test = useTestIntegration()
  const status = statusInfo[integration.status]
  const hasCredentials = integration.requiresCredentials || integration.credentials.length > 0

  const toggle = (enabled: boolean) =>
    update.mutate(
      { code: integration.code, body: { enabled, version: integration.version } },
      {
        onSuccess: () => toast.success(enabled ? `${integration.displayName} activada` : `${integration.displayName} desactivada`),
        onError: (e) =>
          toast.error(
            e instanceof ApiError && e.code === 'CREDENTIALS_REQUIRED' ? 'Configura las credenciales obligatorias antes de activar esta integración.' : errorMessage(e),
          ),
      },
    )

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h3 className="flex items-center gap-2 text-base font-semibold">
              <span className={cn('size-2.5 rounded-full', status.dot)} aria-hidden />
              {integration.displayName}
              <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
            </h3>
            <div className="flex items-center gap-2">
              <label htmlFor={`int-${integration.code}-enabled`} className="text-sm font-normal text-muted-foreground">
                {integration.enabled ? 'Activa' : 'Inactiva'}
              </label>
              <Switch
                id={`int-${integration.code}-enabled`}
                aria-label={`Activar ${integration.displayName}`}
                checked={integration.enabled}
                disabled={update.isPending}
                onCheckedChange={toggle}
              />
            </div>
          </div>
        </CardTitle>
      </CardHeader>
      <CardContent className="grid gap-5">
        {integration.code === 'APIFY' && (
          <p role="note" className="flex items-start gap-2 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm font-medium text-amber-950 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-100">
            <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
            Desactivado por decisión del usuario: por ahora se busca solo en fuentes directas. Los Actors de LinkedIn/Indeed violan los términos de esas plataformas.
          </p>
        )}
        <dl className="grid grid-cols-3 gap-3 text-sm">
          <div>
            <dt className="text-xs text-muted-foreground">Peticiones hoy</dt>
            <dd className="font-medium tabular-nums">{integration.usage.requestsToday}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Peticiones este mes</dt>
            <dd className="font-medium tabular-nums">{integration.usage.requestsThisMonth}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Costo este mes</dt>
            <dd className="font-medium tabular-nums">{formatUsd(integration.usage.costThisMonthUsd)}</dd>
          </div>
        </dl>
        <div className="grid gap-1 text-xs text-muted-foreground">
          {integration.lastSuccessAt && <span>Último éxito: {formatDateTime(integration.lastSuccessAt)}</span>}
          {integration.lastError && (
            <span className="text-destructive">
              Último error{integration.lastErrorAt ? ` (${formatDateTime(integration.lastErrorAt)})` : ''}: {integration.lastError}
            </span>
          )}
        </div>
        {hasCredentials ? <CredentialsForm integration={integration} /> : <p className="text-sm text-muted-foreground">Esta fuente es pública y no necesita credenciales.</p>}
        <LimitsForm key={integration.version} integration={integration} />
        {integration.testable && (
          <div className="flex flex-wrap items-center gap-3 border-t pt-4">
            <Button
              type="button"
              variant="secondary"
              disabled={test.isPending}
              onClick={() =>
                test.mutate(integration.code, {
                  onSuccess: (r) => (r.ok ? toast.success(r.message || 'Conexión correcta') : toast.error(r.message || 'La prueba falló')),
                  onError: (e) => toast.error(errorMessage(e)),
                })
              }
            >
              {test.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <PlugZap aria-hidden />}
              Probar conexión
            </Button>
            {test.data && (
              <span role="status" className={cn('text-sm', test.data.ok ? 'text-emerald-700 dark:text-emerald-400' : 'text-destructive')}>
                {test.data.ok ? '✓ ' : '✗ '}
                {test.data.message}
              </span>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
