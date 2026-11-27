import { ShieldCheck } from 'lucide-react'
import { toast } from 'sonner'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { formatDateTime } from '@/lib/format'
import { errorMessage } from '@/lib/problem'
import type { AiProvider, ConsentDto, ConsentType } from '@/lib/types'
import { useConsents, useUpdateConsents } from './api'

const consentTypes: { type: ConsentType; title: string; description: string }[] = [
  {
    type: 'AI_PROCESS_PROFILE',
    title: 'Procesar mi perfil con IA',
    description: 'Permite usar tu experiencia, proyectos y habilidades para calcular coincidencias y generar cartas.',
  },
  {
    type: 'AI_PROCESS_CV',
    title: 'Procesar mis CV con IA',
    description: 'Permite extraer información de los CV que subas y generar versiones adaptadas.',
  },
]

const providers: { id: AiProvider; name: string; note: string }[] = [
  {
    id: 'deepseek',
    name: 'DeepSeek',
    note: 'Según su política de privacidad, DeepSeek almacena los datos en servidores ubicados en China.',
  },
  {
    id: 'claude',
    name: 'Claude',
    note: 'Claude es un servicio de Anthropic, empresa con sede en Estados Unidos.',
  },
]

export function ConsentsCard() {
  const { data, isLoading, error, refetch } = useConsents()
  const update = useUpdateConsents()

  const find = (type: ConsentType, provider: AiProvider): ConsentDto | undefined =>
    data?.find((c) => c.consentType === type && c.provider === provider)

  const toggle = (type: ConsentType, provider: AiProvider, granted: boolean) => {
    if (!data) return
    const body = data.map((c) => ({
      consentType: c.consentType,
      provider: c.provider,
      granted: c.consentType === type && c.provider === provider ? granted : c.granted,
    }))
    update.mutate(body, {
      onSuccess: () => toast.success(granted ? 'Consentimiento otorgado' : 'Consentimiento revocado'),
      onError: (e) => toast.error(errorMessage(e)),
    })
  }

  return (
    <Card id="consentimientos" className="scroll-mt-20">
      <CardHeader>
        <CardTitle>
          <h2 className="text-base font-semibold">Consentimientos de IA</h2>
        </CardTitle>
        <CardDescription>
          Decide qué proveedor de IA puede procesar tu información. Puedes revocar el permiso en cualquier momento.
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        <p className="flex items-start gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-900 dark:border-emerald-800 dark:bg-emerald-950 dark:text-emerald-100">
          <ShieldCheck className="mt-0.5 size-4 shrink-0" aria-hidden />
          Tus datos de contacto (correo, teléfono, enlaces personales) nunca se envían a ningún proveedor de IA.
        </p>
        {isLoading ? (
          <LoadingState rows={2} />
        ) : error ? (
          <ErrorState error={error} onRetry={() => void refetch()} />
        ) : (
          consentTypes.map((ct) => (
            <fieldset key={ct.type} className="rounded-xl border p-4">
              <legend className="px-1 font-medium">{ct.title}</legend>
              <p className="mb-3 text-sm text-muted-foreground">{ct.description}</p>
              <div className="grid gap-3 md:grid-cols-2">
                {providers.map((p) => {
                  const consent = find(ct.type, p.id)
                  const id = `consent-${ct.type}-${p.id}`
                  return (
                    <div key={p.id} className="flex items-start justify-between gap-3 rounded-lg bg-muted/40 p-3">
                      <div className="grid gap-1">
                        <Label htmlFor={id}>{p.name}</Label>
                        <p id={`${id}-note`} className="text-xs text-muted-foreground">
                          {p.note}
                        </p>
                        {consent?.granted && consent.grantedAt && (
                          <p className="text-xs text-muted-foreground">Otorgado: {formatDateTime(consent.grantedAt)}</p>
                        )}
                      </div>
                      <Switch
                        id={id}
                        aria-label={`${ct.title} con ${p.name}`}
                        aria-describedby={`${id}-note`}
                        checked={!!consent?.granted}
                        disabled={update.isPending || !consent}
                        onCheckedChange={(c) => toggle(ct.type, p.id, c)}
                      />
                    </div>
                  )
                })}
              </div>
            </fieldset>
          ))
        )}
      </CardContent>
    </Card>
  )
}
