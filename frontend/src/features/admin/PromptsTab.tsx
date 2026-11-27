import { useState } from 'react'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { DataTable } from '@/components/DataTable'
import { ErrorState } from '@/components/ErrorState'
import { FormField } from '@/components/FormField'
import { LoadingState } from '@/components/LoadingState'
import { SelectField } from '@/components/SelectField'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { formatDateTime } from '@/lib/format'
import { errorMessage } from '@/lib/problem'
import { promptKeyInfo } from './aiLabels'
import { useActivatePromptVersion, useCreatePromptVersion, usePromptVersions, usePrompts } from './api'
import type { PromptKey, PromptVersionDto } from './types'

function PromptEditor({ promptKey, base }: { promptKey: PromptKey; base: PromptVersionDto | null }) {
  const create = useCreatePromptVersion(promptKey)
  const [system, setSystem] = useState(base?.systemTemplate ?? '')
  const [userT, setUserT] = useState(base?.userTemplate ?? '')
  const [schema, setSchema] = useState(JSON.stringify(base?.outputSchema ?? {}, null, 2))
  const [temperature, setTemperature] = useState(base?.temperature?.toString() ?? '')
  const [notes, setNotes] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const p = `prompt-${promptKey}`

  const save = () => {
    const errs: Record<string, string> = {}
    if (!system.trim()) errs.system = 'Obligatorio'
    if (!userT.trim()) errs.user = 'Obligatorio'
    let outputSchema: object = {}
    try {
      const parsed = JSON.parse(schema) as unknown
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error()
      outputSchema = parsed as object
    } catch {
      errs.schema = 'Debe ser un objeto JSON válido'
    }
    if (temperature && !/^\d+(\.\d+)?$/.test(temperature)) errs.temperature = 'Número inválido'
    setErrors(errs)
    if (Object.keys(errs).length) return
    create.mutate(
      { systemTemplate: system, userTemplate: userT, outputSchema, temperature: temperature ? Number(temperature) : null, notes: notes.trim() || null },
      { onSuccess: (v) => toast.success(`Versión ${v.version} creada (no activa)`), onError: (e) => toast.error(errorMessage(e)) },
    )
  }

  return (
    <div className="grid gap-4">
      <FormField id={`${p}-system`} label="Plantilla de sistema" error={errors.system}>
        <Textarea rows={8} className="font-mono text-xs" spellCheck={false} value={system} onChange={(e) => setSystem(e.target.value)} />
      </FormField>
      <FormField id={`${p}-user`} label="Plantilla de usuario" error={errors.user}>
        <Textarea rows={8} className="font-mono text-xs" spellCheck={false} value={userT} onChange={(e) => setUserT(e.target.value)} />
      </FormField>
      <FormField id={`${p}-schema`} label="Esquema de salida (JSON)" error={errors.schema}>
        <Textarea rows={8} className="font-mono text-xs" spellCheck={false} value={schema} onChange={(e) => setSchema(e.target.value)} />
      </FormField>
      <div className="grid gap-4 sm:grid-cols-[10rem_1fr]">
        <FormField id={`${p}-temp`} label="Temperatura" error={errors.temperature}>
          <Input inputMode="decimal" value={temperature} onChange={(e) => setTemperature(e.target.value)} />
        </FormField>
        <FormField id={`${p}-notes`} label="Notas de la versión">
          <Input value={notes} onChange={(e) => setNotes(e.target.value)} />
        </FormField>
      </div>
      <div className="flex justify-end">
        <Button onClick={save} disabled={create.isPending}>
          {create.isPending && <Loader2 className="animate-spin" aria-hidden />}
          Guardar como nueva versión
        </Button>
      </div>
    </div>
  )
}

export function PromptsTab() {
  const prompts = usePrompts()
  const [selected, setSelected] = useState<PromptKey | ''>('')
  const key = (selected || prompts.data?.[0]?.promptKey) ?? null
  const versions = usePromptVersions(key)
  const activate = useActivatePromptVersion(key ?? 'JOB_ANALYSIS')
  const [baseVersion, setBaseVersion] = useState<number | null>(null)

  if (prompts.isLoading) return <LoadingState />
  if (prompts.error) return <ErrorState error={prompts.error} onRetry={() => void prompts.refetch()} />
  if (!key) return <p className="text-sm text-muted-foreground">No hay prompts configurados.</p>

  const list = versions.data ?? []
  const base = list.find((v) => v.version === baseVersion) ?? list.find((v) => v.active) ?? prompts.data?.find((p) => p.promptKey === key) ?? null

  return (
    <div className="grid gap-4">
      <div className="grid max-w-sm gap-1.5">
        <Label htmlFor="prompt-key">Tarea</Label>
        <SelectField
          id="prompt-key"
          value={key}
          onChange={(v) => {
            setSelected(v)
            setBaseVersion(null)
          }}
          options={(prompts.data ?? []).map((p) => ({ value: p.promptKey, label: `${promptKeyInfo[p.promptKey].label} · v${p.version} activa` }))}
        />
      </div>
      {versions.isLoading ? (
        <LoadingState rows={1} />
      ) : (
        <DataTable
          caption={`Versiones de ${promptKeyInfo[key].label}`}
          rows={list}
          getRowId={(v) => v.version}
          columns={[
            { id: 'v', header: 'Versión', cell: (v) => <span className="font-medium">v{v.version}</span> },
            { id: 'state', header: 'Estado', cell: (v) => (v.active ? <StatusBadge tone="success">Activa</StatusBadge> : <StatusBadge>Inactiva</StatusBadge>) },
            { id: 'created', header: 'Creada', cell: (v) => formatDateTime(v.createdAt), className: 'whitespace-nowrap' },
            { id: 'notes', header: 'Notas', cell: (v) => <span className="text-sm">{v.notes ?? '—'}</span> },
            { id: 'checksum', header: 'Checksum', cell: (v) => <code className="text-xs">{v.checksum.slice(0, 10)}</code> },
            {
              id: 'actions',
              header: <span className="sr-only">Acciones</span>,
              cell: (v) => (
                <div className="flex justify-end gap-1">
                  <Button size="sm" variant="ghost" onClick={() => setBaseVersion(v.version)} aria-label={`Ver versión ${v.version}`}>
                    Ver
                  </Button>
                  {!v.active && (
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={activate.isPending}
                      aria-label={`Activar versión ${v.version}`}
                      onClick={() =>
                        activate.mutate(v.version, {
                          onSuccess: () => toast.success(`Versión ${v.version} activada`),
                          onError: (e) => toast.error(errorMessage(e)),
                        })
                      }
                    >
                      Activar
                    </Button>
                  )}
                </div>
              ),
            },
          ]}
        />
      )}
      <Card>
        <CardHeader>
          <CardTitle>
            <h3 className="text-base font-semibold">{base ? `Basado en v${base.version}` : 'Nueva versión'}</h3>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <PromptEditor key={`${key}-${base?.version ?? 'new'}`} promptKey={key} base={base} />
        </CardContent>
      </Card>
    </div>
  )
}
