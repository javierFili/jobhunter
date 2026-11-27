import { useState, type ReactNode } from 'react'
import { ArrowDown, ArrowUp, Loader2, Plus, X } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { formatPeriod } from '@/lib/format'
import { projectKindLabels } from '@/lib/labels'
import { errorMessage } from '@/lib/problem'
import type { ProfileDto } from '@/lib/types'
import { experiencesApi, projectsApi, useUpdateProfileItems, useUserSkills } from '../api'

interface Option {
  id: number
  label: string
  detail?: string
}

function move<T>(list: T[], from: number, to: number): T[] {
  const next = [...list]
  const [item] = next.splice(from, 1)
  next.splice(to, 0, item)
  return next
}

/** Ordered selection: chosen items (reorderable) + available items. */
function OrderedPicker({ title, options, value, onChange }: { title: string; options: Option[]; value: number[]; onChange: (v: number[]) => void }) {
  const byId = new Map(options.map((o) => [o.id, o]))
  const chosen = value.filter((id) => byId.has(id))
  const available = options.filter((o) => !value.includes(o.id))
  return (
    <div className="grid gap-4 md:grid-cols-2">
      <div>
        <h4 className="mb-2 text-sm font-medium">Incluidos ({chosen.length})</h4>
        {chosen.length === 0 ? (
          <p className="rounded-lg border border-dashed p-4 text-sm text-muted-foreground">Ningún elemento incluido.</p>
        ) : (
          <ol className="grid gap-1.5" aria-label={`${title} incluidos, en orden`}>
            {chosen.map((id, i) => {
              const o = byId.get(id)!
              return (
                <li key={id} className="flex items-center gap-2 rounded-lg border bg-card px-2 py-1.5">
                  <span className="w-5 text-center text-xs text-muted-foreground tabular-nums">{i + 1}</span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm">{o.label}</span>
                    {o.detail && <span className="block truncate text-xs text-muted-foreground">{o.detail}</span>}
                  </span>
                  <Button type="button" variant="ghost" size="icon-xs" disabled={i === 0} onClick={() => onChange(move(chosen, i, i - 1))} aria-label={`Subir ${o.label}`}>
                    <ArrowUp aria-hidden />
                  </Button>
                  <Button type="button" variant="ghost" size="icon-xs" disabled={i === chosen.length - 1} onClick={() => onChange(move(chosen, i, i + 1))} aria-label={`Bajar ${o.label}`}>
                    <ArrowDown aria-hidden />
                  </Button>
                  <Button type="button" variant="ghost" size="icon-xs" onClick={() => onChange(chosen.filter((x) => x !== id))} aria-label={`Quitar ${o.label}`}>
                    <X aria-hidden />
                  </Button>
                </li>
              )
            })}
          </ol>
        )}
      </div>
      <div>
        <h4 className="mb-2 text-sm font-medium">Disponibles ({available.length})</h4>
        {available.length === 0 ? (
          <p className="rounded-lg border border-dashed p-4 text-sm text-muted-foreground">No hay más elementos.</p>
        ) : (
          <ul className="grid max-h-72 gap-1.5 overflow-y-auto">
            {available.map((o) => (
              <li key={o.id} className="flex items-center gap-2 rounded-lg border px-2 py-1.5">
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm">{o.label}</span>
                  {o.detail && <span className="block truncate text-xs text-muted-foreground">{o.detail}</span>}
                </span>
                <Button type="button" variant="outline" size="xs" onClick={() => onChange([...chosen, o.id])} aria-label={`Incluir ${o.label}`}>
                  <Plus aria-hidden /> Incluir
                </Button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}

function ItemsEditor({ profile, onClose }: { profile: ProfileDto; onClose: () => void }) {
  const experiences = experiencesApi.useList()
  const projects = projectsApi.useList()
  const skills = useUserSkills()
  const save = useUpdateProfileItems()
  const [experienceIds, setExperienceIds] = useState(profile.experienceIds)
  const [projectIds, setProjectIds] = useState(profile.projectIds)
  const [skillIds, setSkillIds] = useState(profile.skillIds)

  const loading = experiences.isLoading || projects.isLoading || skills.isLoading
  const tab = (value: string, label: string, count: number): ReactNode => (
    <TabsTrigger value={value}>
      {label} <span className="text-xs text-muted-foreground">({count})</span>
    </TabsTrigger>
  )

  const submit = () =>
    save.mutate(
      { id: profile.id, body: { experienceIds, projectIds, skillIds } },
      {
        onSuccess: () => {
          toast.success('Elementos del perfil actualizados')
          onClose()
        },
        onError: (e) => toast.error(errorMessage(e)),
      },
    )

  return (
    <>
      {loading ? (
        <div className="flex justify-center py-10" role="status">
          <Loader2 className="size-5 animate-spin text-muted-foreground" aria-hidden />
          <span className="sr-only">Cargando…</span>
        </div>
      ) : (
        <Tabs defaultValue="experiences">
          <TabsList>
            {tab('experiences', 'Experiencia', experienceIds.length)}
            {tab('projects', 'Proyectos', projectIds.length)}
            {tab('skills', 'Habilidades', skillIds.length)}
          </TabsList>
          <TabsContent value="experiences" className="pt-2">
            <OrderedPicker
              title="Experiencias"
              value={experienceIds}
              onChange={setExperienceIds}
              options={(experiences.data ?? []).map((e) => ({
                id: e.id,
                label: `${e.positionTitle} · ${e.companyName}`,
                detail: formatPeriod(e.startDate, e.endDate, e.current),
              }))}
            />
          </TabsContent>
          <TabsContent value="projects" className="pt-2">
            <OrderedPicker
              title="Proyectos"
              value={projectIds}
              onChange={setProjectIds}
              options={(projects.data ?? []).map((p) => ({ id: p.id, label: p.name, detail: `Proyecto ${projectKindLabels[p.kind].toLowerCase()}` }))}
            />
          </TabsContent>
          <TabsContent value="skills" className="pt-2">
            <OrderedPicker
              title="Habilidades"
              value={skillIds}
              onChange={setSkillIds}
              options={(skills.data ?? []).map((s) => ({ id: s.skillId, label: s.name }))}
            />
          </TabsContent>
        </Tabs>
      )}
      <DialogFooter>
        <Button variant="outline" onClick={onClose} disabled={save.isPending}>
          Cancelar
        </Button>
        <Button onClick={submit} disabled={save.isPending || loading}>
          {save.isPending && <Loader2 className="animate-spin" aria-hidden />}
          Guardar selección
        </Button>
      </DialogFooter>
    </>
  )
}

export function ProfileItemsDialog({ profile, onOpenChange }: { profile: ProfileDto | null; onOpenChange: (o: boolean) => void }) {
  return (
    <Dialog open={!!profile} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90svh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>Elementos incluidos en «{profile?.name}»</DialogTitle>
          <DialogDescription>Elige qué mostrar en este perfil y en qué orden. El orden se respeta al generar CV y cartas.</DialogDescription>
        </DialogHeader>
        {profile && <ItemsEditor key={profile.id} profile={profile} onClose={() => onOpenChange(false)} />}
      </DialogContent>
    </Dialog>
  )
}
