import { useEffect, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Wrench } from 'lucide-react'
import { toast } from 'sonner'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { DataTable } from '@/components/DataTable'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { FormField } from '@/components/FormField'
import { LoadingState } from '@/components/LoadingState'
import { SelectField } from '@/components/SelectField'
import { VerifiedBadge } from '@/components/StatusBadge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { formatYears } from '@/lib/format'
import { evidenceLevelDescriptions, evidenceLevelLabels, skillCategoryLabels, toOptions } from '@/lib/labels'
import { errorMessage } from '@/lib/problem'
import type { EvidenceLevel, UserSkillDto } from '@/lib/types'
import { handleFormError } from '@/lib/zod'
import { useDeleteUserSkill, useUpsertUserSkill, useUserSkills } from '../api'
import { EvidenceBadge } from '../components/EvidenceBadge'
import { FormDialog } from '../components/FormDialog'
import { RowActions } from '../components/RowActions'
import { SkillSearch } from '../components/SkillSearch'
import { TabToolbar } from '../components/TabToolbar'
import { useSelection } from '../components/useSelection'
import { useVerifyAction } from '../components/useVerifyAction'

const currentYear = new Date().getFullYear()

const schema = z.object({
  evidenceLevel: z.enum(['PROFESSIONAL', 'PROJECT_ONLY', 'ACADEMIC', 'LEARNING']),
  selfRating: z.string(),
  lastUsedYear: z
    .string()
    .refine((v) => v === '' || (/^\d{4}$/.test(v) && +v >= 1970 && +v <= currentYear), `Ingresa un año entre 1970 y ${currentYear}`),
})
type Values = z.infer<typeof schema>

const evidenceOptions = toOptions(evidenceLevelLabels)
const ratingOptions = ['1', '2', '3', '4', '5'].map((v) => ({ value: v, label: `${v} de 5` }))

interface EditingSkill {
  skillId: number
  name: string
  existing: UserSkillDto | null
}

function UserSkillDialog({ skill, onOpenChange }: { skill: EditingSkill | null; onOpenChange: (o: boolean) => void }) {
  const upsert = useUpsertUserSkill()
  const {
    register,
    control,
    handleSubmit,
    setError,
    reset,
    formState: { errors },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { evidenceLevel: 'PROFESSIONAL', selfRating: '', lastUsedYear: '' } })

  useEffect(() => {
    if (skill)
      reset({
        evidenceLevel: skill.existing?.evidenceLevel ?? 'PROFESSIONAL',
        selfRating: skill.existing?.selfRating ? String(skill.existing.selfRating) : '',
        lastUsedYear: skill.existing?.lastUsedYear ? String(skill.existing.lastUsedYear) : '',
      })
  }, [skill, reset])

  const onSubmit = handleSubmit(async (v) => {
    if (!skill) return
    try {
      await upsert.mutateAsync({
        skillId: skill.skillId,
        body: {
          evidenceLevel: v.evidenceLevel as EvidenceLevel,
          selfRating: v.selfRating ? Number(v.selfRating) : null,
          lastUsedYear: v.lastUsedYear ? Number(v.lastUsedYear) : null,
        },
      })
      toast.success(skill.existing ? 'Habilidad actualizada' : `«${skill.name}» agregada a tus habilidades`)
      onOpenChange(false)
    } catch (e) {
      handleFormError(e, setError)
    }
  })

  return (
    <FormDialog
      open={!!skill}
      onOpenChange={onOpenChange}
      title={skill?.existing ? `Editar ${skill.name}` : `Agregar ${skill?.name ?? 'habilidad'}`}
      description="Indica con honestidad dónde usaste esta habilidad: solo la experiencia laboral cuenta como profesional."
      onSubmit={onSubmit}
      submitting={upsert.isPending}
    >
      <fieldset className="grid gap-2">
        <legend className="mb-1 text-sm font-medium">Nivel de evidencia</legend>
        <Controller
          control={control}
          name="evidenceLevel"
          render={({ field }) => (
            <RadioGroup value={field.value} onValueChange={field.onChange} className="grid gap-2">
              {evidenceOptions.map((o) => (
                <Label
                  key={o.value}
                  htmlFor={`evidence-${o.value}`}
                  className="flex cursor-pointer items-start gap-3 rounded-lg border p-3 font-normal has-data-[state=checked]:border-primary has-data-[state=checked]:bg-primary/5"
                >
                  <RadioGroupItem id={`evidence-${o.value}`} value={o.value} className="mt-0.5" />
                  <span>
                    <span className="block font-medium">{o.label}</span>
                    <span className="block text-xs text-muted-foreground">{evidenceLevelDescriptions[o.value]}</span>
                  </span>
                </Label>
              ))}
            </RadioGroup>
          )}
        />
      </fieldset>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="skill-rating" label="Autoevaluación" error={errors.selfRating?.message}>
          <Controller
            control={control}
            name="selfRating"
            render={({ field }) => (
              <SelectField value={field.value} onChange={field.onChange} options={ratingOptions} noneLabel="Sin autoevaluar" />
            )}
          />
        </FormField>
        <FormField id="skill-last-used" label="Último año de uso" error={errors.lastUsedYear?.message}>
          <Input inputMode="numeric" placeholder={String(currentYear)} {...register('lastUsedYear')} />
        </FormField>
      </div>
    </FormDialog>
  )
}

export function SkillsTab() {
  const { data, isLoading, error, refetch } = useUserSkills()
  const remove = useDeleteUserSkill()
  const [editing, setEditing] = useState<EditingSkill | null>(null)
  const [deleting, setDeleting] = useState<UserSkillDto | null>(null)
  const [filter, setFilter] = useState<EvidenceLevel | ''>('')
  const selection = useSelection<number>()
  const verify = useVerifyAction('skillIds', selection.clear)

  const rows = (data ?? []).filter((s) => !filter || s.evidenceLevel === filter)

  return (
    <section aria-labelledby="tab-skills-title">
      <h2 id="tab-skills-title" className="sr-only">
        Habilidades
      </h2>
      <TabToolbar
        description="Los años profesionales se calculan automáticamente a partir de tu experiencia laboral."
        selectedCount={selection.selected.length}
        onVerify={() => verify.run(selection.selected)}
        verifying={verify.pending}
        extra={
          <>
            <div className="w-48">
              <SelectField
                aria-label="Filtrar por nivel de evidencia"
                value={filter}
                onChange={setFilter}
                options={evidenceOptions}
                noneLabel="Todos los niveles"
              />
            </div>
            <SkillSearch
              id="skill-add"
              excludeIds={(data ?? []).map((s) => s.skillId)}
              onPick={(s) => setEditing({ skillId: s.id, name: s.name, existing: null })}
            />
          </>
        }
      />
      {isLoading ? (
        <LoadingState />
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <DataTable
          caption="Mis habilidades"
          rows={rows}
          getRowId={(r) => r.skillId}
          getRowLabel={(r) => r.name}
          selectable
          selected={selection.selected}
          onSelectedChange={selection.setSelected}
          empty={
            <EmptyState
              icon={Wrench}
              title={filter ? 'No hay habilidades con este nivel' : 'Aún no agregaste habilidades'}
              description="Busca en el catálogo con «Agregar habilidad» o crea una nueva."
            />
          }
          columns={[
            {
              id: 'name',
              header: 'Habilidad',
              cell: (r) => (
                <div>
                  <div className="font-medium">{r.name}</div>
                  <div className="text-xs text-muted-foreground">{skillCategoryLabels[r.category]}</div>
                </div>
              ),
            },
            { id: 'evidence', header: 'Nivel de evidencia', cell: (r) => <EvidenceBadge level={r.evidenceLevel} /> },
            {
              id: 'years',
              header: 'Años profesionales',
              className: 'whitespace-nowrap',
              cell: (r) => <span className="tabular-nums">{formatYears(r.yearsProfessional)}</span>,
            },
            { id: 'rating', header: 'Autoevaluación', cell: (r) => (r.selfRating ? `${r.selfRating}/5` : '—') },
            { id: 'lastUsed', header: 'Último uso', cell: (r) => r.lastUsedYear ?? '—' },
            { id: 'verified', header: 'Verificación', cell: (r) => <VerifiedBadge verified={r.verified} /> },
            {
              id: 'actions',
              header: <span className="sr-only">Acciones</span>,
              cell: (r) => (
                <RowActions
                  name={r.name}
                  onEdit={() => setEditing({ skillId: r.skillId, name: r.name, existing: r })}
                  onDelete={() => setDeleting(r)}
                />
              ),
            },
          ]}
        />
      )}
      <UserSkillDialog skill={editing} onOpenChange={(o) => !o && setEditing(null)} />
      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(o) => !o && setDeleting(null)}
        title="¿Quitar esta habilidad?"
        description={deleting ? `«${deleting.name}» se quitará de tu perfil (no del catálogo).` : undefined}
        confirmLabel="Quitar"
        destructive
        pending={remove.isPending}
        onConfirm={() =>
          deleting &&
          remove.mutate(deleting.skillId, {
            onSuccess: () => {
              toast.success('Habilidad quitada')
              setDeleting(null)
            },
            onError: (e) => toast.error(errorMessage(e)),
          })
        }
      />
    </section>
  )
}
