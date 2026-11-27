import { useState, type FormEvent } from 'react'
import { AlertTriangle, Pencil, Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { DataTable } from '@/components/DataTable'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { FormField } from '@/components/FormField'
import { LoadingState } from '@/components/LoadingState'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { FormDialog } from '@/features/profile/components/FormDialog'
import { formatDateTime } from '@/lib/format'
import { errorMessage } from '@/lib/problem'
import { useApifyActors, useDeleteApifyActor, useSaveApifyActor } from './api'
import type { ApifyActorDto, ApifyActorRequest } from './types'

const LEGAL_WARNING =
  'Usar Actors que extraen datos de LinkedIn, Indeed u otras plataformas puede violar sus términos de servicio. Antes de activar un Actor, documenta la evaluación legal y de costos en «Notas de evaluación».'

interface FormState {
  actorId: string
  displayName: string
  platform: string
  enabled: boolean
  inputTemplate: string
  fieldMapping: string
  maxItems: string
  maxChargeUsd: string
  timeoutSecs: string
  pricingModel: string
  assessmentNotes: string
}

const toForm = (a: ApifyActorDto | null): FormState => ({
  actorId: a?.actorId ?? '',
  displayName: a?.displayName ?? '',
  platform: a?.platform ?? '',
  enabled: a?.enabled ?? false,
  inputTemplate: JSON.stringify(a?.inputTemplate ?? {}, null, 2),
  fieldMapping: JSON.stringify(a?.fieldMapping ?? {}, null, 2),
  maxItems: a?.maxItems?.toString() ?? '',
  maxChargeUsd: a?.maxChargeUsd?.toString() ?? '',
  timeoutSecs: a?.timeoutSecs?.toString() ?? '',
  pricingModel: a?.pricingModel ?? '',
  assessmentNotes: a?.assessmentNotes ?? '',
})

function ActorForm({ actor, onClose }: { actor: ApifyActorDto | null; onClose: () => void }) {
  const [f, setF] = useState<FormState>(() => toForm(actor))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const save = useSaveApifyActor()
  const set = (patch: Partial<FormState>) => setF((s) => ({ ...s, ...patch }))

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const errs: Record<string, string> = {}
    if (!f.actorId.trim()) errs.actorId = 'Ingresa el ID del Actor (ej. usuario~actor)'
    if (!f.displayName.trim()) errs.displayName = 'Ingresa un nombre'
    if (!f.platform.trim()) errs.platform = 'Indica la plataforma'
    if (f.enabled && !f.assessmentNotes.trim()) errs.assessmentNotes = 'Para activar el Actor documenta la evaluación legal y de costos'
    const json = (text: string, key: string) => {
      try {
        return JSON.parse(text || '{}') as unknown
      } catch {
        errs[key] = 'JSON inválido'
        return null
      }
    }
    const inputTemplate = json(f.inputTemplate, 'inputTemplate')
    const fieldMapping = json(f.fieldMapping, 'fieldMapping')
    const num = (v: string, key: string, decimals = false) => {
      if (!v.trim()) return null
      if (!(decimals ? /^\d+(\.\d+)?$/ : /^\d+$/).test(v.trim())) {
        errs[key] = 'Número inválido'
        return null
      }
      return Number(v)
    }
    const body: ApifyActorRequest = {
      actorId: f.actorId.trim(),
      displayName: f.displayName.trim(),
      platform: f.platform.trim(),
      enabled: f.enabled,
      inputTemplate,
      fieldMapping,
      maxItems: num(f.maxItems, 'maxItems'),
      maxChargeUsd: num(f.maxChargeUsd, 'maxChargeUsd', true),
      timeoutSecs: num(f.timeoutSecs, 'timeoutSecs'),
      pricingModel: f.pricingModel.trim() || null,
      assessmentNotes: f.assessmentNotes.trim() || null,
      ...(actor ? { version: actor.version } : {}),
    }
    setErrors(errs)
    if (Object.keys(errs).length) return
    save.mutate(
      { id: actor?.id, body },
      {
        onSuccess: () => {
          toast.success(actor ? 'Actor actualizado' : 'Actor agregado')
          onClose()
        },
        onError: (err) => toast.error(errorMessage(err)),
      },
    )
  }

  return (
    <FormDialog open onOpenChange={(o) => !o && onClose()} title={actor ? 'Editar Actor de Apify' : 'Agregar Actor de Apify'} onSubmit={submit} submitting={save.isPending} wide>
      <p role="note" className="flex items-start gap-2 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-950 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-100">
        <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
        {LEGAL_WARNING}
      </p>
      <div className="grid gap-4 sm:grid-cols-3">
        <FormField id="ac-id" label="ID del Actor" required error={errors.actorId}>
          <Input value={f.actorId} onChange={(e) => set({ actorId: e.target.value })} />
        </FormField>
        <FormField id="ac-name" label="Nombre" required error={errors.displayName}>
          <Input value={f.displayName} onChange={(e) => set({ displayName: e.target.value })} />
        </FormField>
        <FormField id="ac-platform" label="Plataforma" required error={errors.platform}>
          <Input value={f.platform} onChange={(e) => set({ platform: e.target.value })} />
        </FormField>
        <FormField id="ac-max-items" label="Máx. elementos" error={errors.maxItems}>
          <Input inputMode="numeric" value={f.maxItems} onChange={(e) => set({ maxItems: e.target.value })} />
        </FormField>
        <FormField id="ac-max-charge" label="Cargo máx. (USD)" error={errors.maxChargeUsd}>
          <Input inputMode="decimal" value={f.maxChargeUsd} onChange={(e) => set({ maxChargeUsd: e.target.value })} />
        </FormField>
        <FormField id="ac-timeout" label="Timeout (s)" error={errors.timeoutSecs}>
          <Input inputMode="numeric" value={f.timeoutSecs} onChange={(e) => set({ timeoutSecs: e.target.value })} />
        </FormField>
      </div>
      <FormField id="ac-pricing" label="Modelo de precios">
        <Input value={f.pricingModel} onChange={(e) => set({ pricingModel: e.target.value })} />
      </FormField>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="ac-input" label="Plantilla de entrada (JSON)" error={errors.inputTemplate}>
          <Textarea rows={5} className="font-mono text-xs" spellCheck={false} value={f.inputTemplate} onChange={(e) => set({ inputTemplate: e.target.value })} />
        </FormField>
        <FormField id="ac-mapping" label="Mapeo de campos (JSON)" error={errors.fieldMapping}>
          <Textarea rows={5} className="font-mono text-xs" spellCheck={false} value={f.fieldMapping} onChange={(e) => set({ fieldMapping: e.target.value })} />
        </FormField>
      </div>
      <FormField id="ac-notes" label="Notas de evaluación" hint="Términos de la plataforma, riesgos y costos estimados." error={errors.assessmentNotes}>
        <Textarea rows={3} value={f.assessmentNotes} onChange={(e) => set({ assessmentNotes: e.target.value })} />
      </FormField>
      <div className="flex items-center gap-2">
        <Switch id="ac-enabled" checked={f.enabled} onCheckedChange={(enabled) => set({ enabled })} />
        <Label htmlFor="ac-enabled" className="font-normal">
          Actor activo
        </Label>
      </div>
    </FormDialog>
  )
}

export function ApifyActorsTab() {
  const { data, isLoading, error, refetch } = useApifyActors()
  const remove = useDeleteApifyActor()
  const [editing, setEditing] = useState<ApifyActorDto | null | 'new'>(null)
  const [deleting, setDeleting] = useState<ApifyActorDto | null>(null)

  return (
    <div className="grid gap-4">
      <p role="note" className="flex items-start gap-2 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-950 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-100">
        <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
        {LEGAL_WARNING}
      </p>
      <div className="flex justify-end">
        <Button onClick={() => setEditing('new')}>
          <Plus aria-hidden /> Agregar Actor
        </Button>
      </div>
      {isLoading ? (
        <LoadingState rows={2} />
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <DataTable
          caption="Actors de Apify"
          rows={data ?? []}
          getRowId={(a) => a.id}
          empty={<EmptyState icon={AlertTriangle} title="No hay Actors configurados" description="Por ahora se busca solo en fuentes directas." />}
          columns={[
            {
              id: 'name',
              header: 'Actor',
              cell: (a) => (
                <div>
                  <div className="font-medium">{a.displayName}</div>
                  <div className="text-xs text-muted-foreground">
                    {a.actorId} · {a.platform}
                  </div>
                </div>
              ),
            },
            { id: 'limits', header: 'Límites', cell: (a) => <span className="text-xs">{[a.maxItems && `${a.maxItems} elementos`, a.maxChargeUsd != null && `máx. US$${a.maxChargeUsd}`].filter(Boolean).join(' · ') || '—'}</span> },
            { id: 'assessed', header: 'Evaluado', cell: (a) => (a.assessedAt ? formatDateTime(a.assessedAt) : <StatusBadge tone="warning">Sin evaluar</StatusBadge>) },
            { id: 'enabled', header: 'Estado', cell: (a) => (a.enabled ? <StatusBadge tone="success">Activo</StatusBadge> : <StatusBadge>Inactivo</StatusBadge>) },
            {
              id: 'actions',
              header: <span className="sr-only">Acciones</span>,
              cell: (a) => (
                <div className="flex justify-end gap-1">
                  <Button variant="ghost" size="icon-sm" aria-label={`Editar ${a.displayName}`} onClick={() => setEditing(a)}>
                    <Pencil aria-hidden />
                  </Button>
                  <Button variant="ghost" size="icon-sm" className="text-destructive" aria-label={`Eliminar ${a.displayName}`} onClick={() => setDeleting(a)}>
                    <Trash2 aria-hidden />
                  </Button>
                </div>
              ),
            },
          ]}
        />
      )}
      {editing && <ActorForm actor={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(o) => !o && setDeleting(null)}
        title="¿Eliminar este Actor?"
        confirmLabel="Eliminar"
        destructive
        pending={remove.isPending}
        onConfirm={() =>
          deleting &&
          remove.mutate(deleting.id, {
            onSuccess: () => {
              toast.success('Actor eliminado')
              setDeleting(null)
            },
            onError: (e) => toast.error(errorMessage(e)),
          })
        }
      />
    </div>
  )
}
