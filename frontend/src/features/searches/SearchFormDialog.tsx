import { useState } from 'react'
import { Sparkles } from 'lucide-react'
import { toast } from 'sonner'
import { z } from 'zod'
import { FormField } from '@/components/FormField'
import { SelectField } from '@/components/SelectField'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Switch } from '@/components/ui/switch'
import { useSources } from '@/features/jobs/api'
import { FiltersFields } from '@/features/jobs/FiltersFields'
import { profilesApi } from '@/features/profile/api'
import { FormDialog } from '@/features/profile/components/FormDialog'
import { ApiError, errorMessage } from '@/lib/problem'
import { useCreateSearch, useUpdateSearch } from './api'
import { SEARCH_TEMPLATES, defaultSearchRequest, scheduleLabels } from './labels'
import type { SavedSearchDto, SavedSearchRequest, ScheduleType } from './types'

const schema = z
  .object({
    name: z.string().trim().min(1, 'Ingresa un nombre').max(100, 'Máximo 100 caracteres'),
    keywords: z.string().max(200, 'Máximo 200 caracteres').nullable().optional(),
    scheduleType: z.enum(['MANUAL', 'INTERVAL', 'DAILY']),
    intervalMinutes: z.number().nullable().optional(),
    dailyTime: z.string().nullable().optional(),
    maxResultsPerSource: z.number({ error: 'Ingresa un número' }).int('Debe ser un número entero').min(1, 'Mínimo 1').max(100, 'Máximo 100'),
  })
  .superRefine((v, ctx) => {
    if (v.scheduleType === 'INTERVAL' && (v.intervalMinutes == null || !Number.isInteger(v.intervalMinutes) || v.intervalMinutes < 60)) {
      ctx.addIssue({ code: 'custom', path: ['intervalMinutes'], message: 'El intervalo mínimo es de 60 minutos' })
    }
    if (v.scheduleType === 'DAILY' && !/^([01]\d|2[0-3]):[0-5]\d$/.test(v.dailyTime ?? '')) {
      ctx.addIssue({ code: 'custom', path: ['dailyTime'], message: 'Ingresa una hora válida (HH:mm)' })
    }
  })

const TIMEZONES = ['America/La_Paz', 'America/Argentina/Buenos_Aires', 'America/Lima', 'America/Bogota', 'America/Mexico_City', 'America/Sao_Paulo', 'Europe/Madrid', 'Europe/Rome', 'UTC']

function toRequest(s: SavedSearchDto | null): SavedSearchRequest {
  if (!s) return defaultSearchRequest()
  const base = defaultSearchRequest()
  return {
    name: s.name,
    profileId: s.profileId ?? null,
    keywords: s.keywords ?? '',
    filters: { ...base.filters, ...s.filters },
    sourceCodes: s.sourceCodes,
    scheduleType: s.scheduleType,
    intervalMinutes: s.intervalMinutes ?? base.intervalMinutes,
    dailyTime: s.dailyTime ?? base.dailyTime,
    timezone: s.timezone ?? base.timezone,
    enabled: s.enabled,
    maxResultsPerSource: s.maxResultsPerSource,
    notifyOnNew: s.notifyOnNew,
  }
}

function SearchForm({ search, onOpenChange }: { search: SavedSearchDto | null; onOpenChange: (o: boolean) => void }) {
  const [form, setForm] = useState<SavedSearchRequest>(() => toRequest(search))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const sources = useSources()
  const profiles = profilesApi.useList()
  const create = useCreateSearch()
  const update = useUpdateSearch()
  const set = (patch: Partial<SavedSearchRequest>) => setForm((f) => ({ ...f, ...patch }))

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    const r = schema.safeParse(form)
    if (!r.success) {
      const errs: Record<string, string> = {}
      for (const i of r.error.issues) errs[String(i.path[0])] ??= i.message
      setErrors(errs)
      return
    }
    setErrors({})
    const body: SavedSearchRequest = {
      ...form,
      name: form.name.trim(),
      keywords: form.keywords?.trim() || null,
      intervalMinutes: form.scheduleType === 'INTERVAL' ? form.intervalMinutes : null,
      dailyTime: form.scheduleType === 'DAILY' ? form.dailyTime : null,
      timezone: form.timezone || 'America/La_Paz',
    }
    try {
      if (search) {
        await update.mutateAsync({ id: search.id, body: { ...body, version: search.version } })
        toast.success('Búsqueda actualizada')
      } else {
        await create.mutateAsync(body)
        toast.success('Búsqueda creada')
      }
      onOpenChange(false)
    } catch (err) {
      if (err instanceof ApiError) {
        const errs: Record<string, string> = {}
        for (const fe of err.fieldErrors) errs[fe.field] = fe.message
        setErrors(errs)
      }
      toast.error(errorMessage(err))
    }
  }

  return (
    <FormDialog
      open
      onOpenChange={onOpenChange}
      title={search ? 'Editar búsqueda' : 'Nueva búsqueda'}
      description="Las búsquedas consultan las fuentes elegidas y guardan las ofertas nuevas."
      onSubmit={submit}
      submitting={create.isPending || update.isPending}
      wide
    >
      {!search && (
        <div className="grid gap-2">
          <span className="text-sm font-medium">Plantillas rápidas</span>
          <div className="flex flex-wrap gap-2">
            {SEARCH_TEMPLATES.map((t) => (
              <Button key={t.name} type="button" variant="outline" size="sm" onClick={() => setForm((f) => t.apply({ ...defaultSearchRequest(), sourceCodes: f.sourceCodes }))}>
                <Sparkles aria-hidden /> {t.name}
              </Button>
            ))}
          </div>
        </div>
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="ss-name" label="Nombre" required error={errors.name}>
          <Input value={form.name} onChange={(e) => set({ name: e.target.value })} />
        </FormField>
        <FormField id="ss-keywords" label="Palabras clave" error={errors.keywords}>
          <Input value={form.keywords ?? ''} onChange={(e) => set({ keywords: e.target.value })} />
        </FormField>
        <FormField id="ss-profile" label="Perfil profesional" hint="Se usará para calcular afinidad (Fase 4).">
          <SelectField
            value={form.profileId != null ? String(form.profileId) : ''}
            onChange={(v) => set({ profileId: v ? Number(v) : null })}
            noneLabel="Ninguno"
            options={(profiles.data ?? []).map((p) => ({ value: String(p.id), label: p.name }))}
          />
        </FormField>
      </div>

      <fieldset className="grid gap-2 rounded-lg border p-4">
        <legend className="px-1 text-sm font-medium">Fuentes</legend>
        <p className="text-xs text-muted-foreground">Si no eliges ninguna, se usan todas las fuentes disponibles.</p>
        <ul className="grid gap-2 sm:grid-cols-2">
          {(sources.data ?? []).map((s) => {
            const id = `ss-src-${s.code}`
            return (
              <li key={s.code} className="flex items-start gap-2">
                <Checkbox
                  id={id}
                  disabled={!s.usable}
                  checked={form.sourceCodes.includes(s.code)}
                  onCheckedChange={(c) => set({ sourceCodes: c === true ? [...form.sourceCodes, s.code] : form.sourceCodes.filter((x) => x !== s.code) })}
                  className="mt-0.5"
                />
                <Label htmlFor={id} className="grid gap-0.5 font-normal">
                  <span>{s.displayName}</span>
                  {!s.usable && s.reason && <span className="text-xs text-muted-foreground">No disponible: {s.reason}</span>}
                </Label>
              </li>
            )
          })}
        </ul>
      </fieldset>

      <fieldset className="grid gap-3 rounded-lg border p-4">
        <legend className="px-1 text-sm font-medium">Filtros</legend>
        <FiltersFields idPrefix="ss" value={form.filters} onChange={(filters) => set({ filters })} withCompanies />
      </fieldset>

      <fieldset className="grid gap-3 rounded-lg border p-4">
        <legend className="px-1 text-sm font-medium">Programación</legend>
        <RadioGroup value={form.scheduleType} onValueChange={(v) => set({ scheduleType: v as ScheduleType })} className="flex flex-wrap gap-4" aria-label="Tipo de programación">
          {(Object.keys(scheduleLabels) as ScheduleType[]).map((t) => (
            <div key={t} className="flex items-center gap-2">
              <RadioGroupItem id={`ss-sched-${t}`} value={t} />
              <Label htmlFor={`ss-sched-${t}`} className="font-normal">
                {scheduleLabels[t]}
              </Label>
            </div>
          ))}
        </RadioGroup>
        <div className="grid gap-4 sm:grid-cols-3">
          {form.scheduleType === 'INTERVAL' && (
            <FormField id="ss-interval" label="Cada (minutos)" hint="Mínimo 60." error={errors.intervalMinutes}>
              <Input type="number" min={60} step={30} value={form.intervalMinutes ?? ''} onChange={(e) => set({ intervalMinutes: e.target.value === '' ? null : Number(e.target.value) })} />
            </FormField>
          )}
          {form.scheduleType === 'DAILY' && (
            <>
              <FormField id="ss-time" label="Hora" error={errors.dailyTime}>
                <Input type="time" value={form.dailyTime ?? ''} onChange={(e) => set({ dailyTime: e.target.value })} />
              </FormField>
              <FormField id="ss-tz" label="Zona horaria" error={errors.timezone}>
                <SelectField value={form.timezone ?? ''} onChange={(v) => set({ timezone: v || 'America/La_Paz' })} options={TIMEZONES.map((t) => ({ value: t, label: t.replace(/_/g, ' ') }))} />
              </FormField>
            </>
          )}
          <FormField id="ss-max" label="Máx. resultados por fuente" hint="Entre 1 y 100." error={errors.maxResultsPerSource}>
            <Input type="number" min={1} max={100} value={Number.isNaN(form.maxResultsPerSource) ? '' : form.maxResultsPerSource} onChange={(e) => set({ maxResultsPerSource: e.target.value === '' ? Number.NaN : Number(e.target.value) })} />
          </FormField>
        </div>
        <div className="flex flex-wrap gap-6">
          <div className="flex items-center gap-2">
            <Switch id="ss-enabled" checked={form.enabled} onCheckedChange={(enabled) => set({ enabled })} />
            <Label htmlFor="ss-enabled" className="font-normal">
              Búsqueda activa
            </Label>
          </div>
          <div className="flex items-center gap-2">
            <Switch id="ss-notify" checked={form.notifyOnNew} onCheckedChange={(notifyOnNew) => set({ notifyOnNew })} />
            <Label htmlFor="ss-notify" className="font-normal">
              Notificarme si hay ofertas nuevas
            </Label>
          </div>
        </div>
      </fieldset>
    </FormDialog>
  )
}

/** Mounted only while open so each opening starts from fresh state. */
export function SearchFormDialog({ open, onOpenChange, search }: { open: boolean; onOpenChange: (o: boolean) => void; search: SavedSearchDto | null }) {
  if (!open) return null
  return <SearchForm key={search?.id ?? 'new'} search={search} onOpenChange={onOpenChange} />
}
