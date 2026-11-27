import { useState, type FormEvent } from 'react'
import { Info, Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { ErrorState } from '@/components/ErrorState'
import { FormField } from '@/components/FormField'
import { LoadingState } from '@/components/LoadingState'
import { SelectField } from '@/components/SelectField'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { toOptions } from '@/lib/labels'
import { ApiError, errorMessage } from '@/lib/problem'
import { AI_PRIVACY_NOTE, effortLabels, promptKeyInfo, providerLabels } from './aiLabels'
import { useAiModels, useAiRoutes, useUpdateAiRoute } from './api'
import type { AiProvider, AiRouteDto, Effort } from './types'

const providerOptions = toOptions(providerLabels)

/** Free-text model id with suggestions from GET /admin/ai/models (native datalist). */
function ModelInput({ id, provider, value, onChange, disabled }: { id: string; provider: AiProvider | ''; value: string; onChange: (v: string) => void; disabled?: boolean }) {
  const models = useAiModels((provider || 'DEEPSEEK') as AiProvider)
  const listId = `${id}-list`
  return (
    <>
      <Input id={id} list={listId} value={value} onChange={(e) => onChange(e.target.value)} disabled={disabled} autoComplete="off" spellCheck={false} />
      <datalist id={listId}>{provider && (models.data?.models ?? []).map((m) => <option key={m} value={m} />)}</datalist>
    </>
  )
}

function RouteCard({ route }: { route: AiRouteDto }) {
  const update = useUpdateAiRoute()
  const [provider, setProvider] = useState<AiProvider>(route.provider)
  const [model, setModel] = useState(route.model)
  const [thinking, setThinking] = useState<'ON' | 'OFF' | ''>(route.thinking ?? '')
  const [effort, setEffort] = useState<Effort | ''>(route.effort ?? '')
  const [fallbackProvider, setFallbackProvider] = useState<AiProvider | ''>(route.fallbackProvider ?? '')
  const [fallbackModel, setFallbackModel] = useState(route.fallbackModel ?? '')
  const [maxTokens, setMaxTokens] = useState(route.maxOutputTokens?.toString() ?? '')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const info = promptKeyInfo[route.promptKey]
  const p = `route-${route.promptKey}`

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const errs: Record<string, string> = {}
    if (!model.trim()) errs.model = 'Indica el modelo'
    if (fallbackProvider && !fallbackModel.trim()) errs.fallbackModel = 'Indica el modelo de respaldo'
    if (maxTokens && (!/^\d+$/.test(maxTokens) || Number(maxTokens) < 1)) errs.maxTokens = 'Número entero mayor a 0'
    setErrors(errs)
    if (Object.keys(errs).length) return
    update.mutate(
      {
        key: route.promptKey,
        body: {
          provider,
          model: model.trim(),
          thinking: provider === 'DEEPSEEK' ? thinking || null : null,
          effort: provider === 'CLAUDE' ? effort || null : null,
          fallbackProvider: fallbackProvider || null,
          fallbackModel: fallbackProvider ? fallbackModel.trim() : null,
          maxOutputTokens: maxTokens ? Number(maxTokens) : null,
          version: route.version,
        },
      },
      {
        onSuccess: () => toast.success(`Ruta «${info.label}» guardada`),
        onError: (err) =>
          toast.error(
            err instanceof ApiError && err.code === 'INTEGRATION_DISABLED'
              ? 'El proveedor elegido está desactivado o no tiene credenciales. Actívalo en la pestaña Integraciones.'
              : err instanceof ApiError && err.code === 'CONFLICT'
                ? 'Otra persona modificó esta ruta. Se recargaron los datos; revisa y vuelve a guardar.'
                : errorMessage(err),
          ),
      },
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h3 className="flex flex-wrap items-center gap-2 text-base font-semibold">
            {info.label}
            {!route.implemented && <StatusBadge tone="neutral">Fase 5</StatusBadge>}
          </h3>
        </CardTitle>
        <CardDescription>{info.help}</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={submit} noValidate className="grid gap-4" aria-label={`Ruta de IA: ${info.label}`}>
          <div className="grid gap-4 sm:grid-cols-3">
            <FormField id={`${p}-provider`} label="Proveedor">
              <SelectField value={provider} onChange={(v) => v && setProvider(v)} options={providerOptions} />
            </FormField>
            <FormField id={`${p}-model`} label="Modelo" error={errors.model}>
              <ModelInput id={`${p}-model`} provider={provider} value={model} onChange={setModel} />
            </FormField>
            {provider === 'DEEPSEEK' ? (
              <FormField id={`${p}-thinking`} label="Pensamiento">
                <SelectField
                  value={thinking}
                  onChange={setThinking}
                  noneLabel="Predeterminado"
                  options={[
                    { value: 'ON', label: 'Activado' },
                    { value: 'OFF', label: 'Desactivado' },
                  ]}
                />
              </FormField>
            ) : (
              <FormField id={`${p}-effort`} label="Esfuerzo">
                <SelectField value={effort} onChange={setEffort} noneLabel="Predeterminado" options={toOptions(effortLabels)} />
              </FormField>
            )}
            <FormField id={`${p}-fallback-provider`} label="Proveedor de respaldo">
              <SelectField value={fallbackProvider} onChange={setFallbackProvider} noneLabel="Sin respaldo" options={providerOptions} />
            </FormField>
            <FormField id={`${p}-fallback-model`} label="Modelo de respaldo" error={errors.fallbackModel}>
              <ModelInput id={`${p}-fallback-model`} provider={fallbackProvider} value={fallbackModel} onChange={setFallbackModel} disabled={!fallbackProvider} />
            </FormField>
            <FormField id={`${p}-max-tokens`} label="Máx. tokens de salida" error={errors.maxTokens}>
              <Input inputMode="numeric" value={maxTokens} onChange={(e) => setMaxTokens(e.target.value)} />
            </FormField>
          </div>
          <div className="flex justify-end">
            <Button type="submit" variant="outline" disabled={update.isPending}>
              {update.isPending && <Loader2 className="animate-spin" aria-hidden />}
              Guardar ruta
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}

export function AiRoutesTab() {
  const { data, isLoading, error, refetch } = useAiRoutes()
  return (
    <div className="grid gap-4">
      <p className="flex items-start gap-2 rounded-lg border border-sky-200 bg-sky-50 px-3 py-2 text-sm text-sky-900 dark:border-sky-800 dark:bg-sky-950 dark:text-sky-100">
        <Info className="mt-0.5 size-4 shrink-0" aria-hidden />
        {AI_PRIVACY_NOTE}
      </p>
      {isLoading ? (
        <LoadingState />
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        (data ?? []).map((r) => <RouteCard key={`${r.promptKey}-${r.version}`} route={r} />)
      )}
    </div>
  )
}
