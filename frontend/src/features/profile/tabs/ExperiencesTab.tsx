import { Briefcase, MapPin } from 'lucide-react'
import { toast } from 'sonner'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { formatPeriod } from '@/lib/format'
import { countryName, employmentTypeLabels, workModalityLabels } from '@/lib/labels'
import { errorMessage } from '@/lib/problem'
import type { ExperienceDto } from '@/lib/types'
import { experiencesApi } from '../api'
import { ItemCard } from '../components/ItemCard'
import { TabToolbar } from '../components/TabToolbar'
import { useEntityDialogs } from '../components/useEntityDialogs'
import { useSelection } from '../components/useSelection'
import { useVerifyAction } from '../components/useVerifyAction'
import { ExperienceForm } from './ExperienceForm'

export function ExperiencesTab() {
  const { data, isLoading, error, refetch } = experiencesApi.useList()
  const remove = experiencesApi.useDelete()
  const dialogs = useEntityDialogs<ExperienceDto>()
  const selection = useSelection<number>()
  const verify = useVerifyAction('experienceIds', selection.clear)

  return (
    <section aria-labelledby="tab-experience-title">
      <h2 id="tab-experience-title" className="sr-only">
        Experiencia laboral
      </h2>
      <TabToolbar
        description="Empleos y contratos remunerados. Esta es la única sección que cuenta como experiencia profesional."
        addLabel="Agregar experiencia"
        onAdd={dialogs.openCreate}
        selectedCount={selection.selected.length}
        onVerify={() => verify.run(selection.selected)}
        verifying={verify.pending}
      />
      {isLoading ? (
        <LoadingState />
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : !data?.length ? (
        <EmptyState
          icon={Briefcase}
          title="Aún no registraste experiencia laboral"
          description="Agrega tus empleos para calcular tus años de experiencia profesional por habilidad."
          action={<Button onClick={dialogs.openCreate}>Agregar experiencia</Button>}
        />
      ) : (
        <div className="grid gap-3">
          {data.map((e) => (
            <ItemCard
              key={e.id}
              name={`${e.positionTitle} en ${e.companyName}`}
              title={e.positionTitle}
              subtitle={
                <>
                  {e.companyUrl ? (
                    <a href={e.companyUrl} target="_blank" rel="noreferrer" className="underline-offset-4 hover:underline">
                      {e.companyName}
                    </a>
                  ) : (
                    e.companyName
                  )}{' '}
                  · {formatPeriod(e.startDate, e.endDate, e.current)}
                </>
              }
              badges={
                <>
                  <StatusBadge tone="info">{employmentTypeLabels[e.employmentType]}</StatusBadge>
                  <StatusBadge>{workModalityLabels[e.workModality]}</StatusBadge>
                  {e.source === 'CV_IMPORT' && <StatusBadge tone="violet">Importado de CV</StatusBadge>}
                </>
              }
              meta={
                (e.location || e.country) && (
                  <span className="inline-flex items-center gap-1">
                    <MapPin className="size-3" aria-hidden />
                    {[e.location, e.country && countryName(e.country)].filter(Boolean).join(', ')}
                  </span>
                )
              }
              verified={e.verified}
              selected={selection.isSelected(e.id)}
              onSelectedChange={(on) => selection.toggle(e.id, on)}
              onEdit={() => dialogs.openEdit(e)}
              onDelete={() => dialogs.askDelete(e)}
            >
              {e.description && <p className="text-sm whitespace-pre-line">{e.description}</p>}
              {e.achievements.length > 0 && (
                <ul className="list-disc space-y-0.5 pl-5 text-sm">
                  {e.achievements.map((a, i) => (
                    <li key={i}>{a}</li>
                  ))}
                </ul>
              )}
              {e.skills.length > 0 && (
                <ul className="flex flex-wrap gap-1" aria-label="Habilidades">
                  {e.skills.map((s) => (
                    <li key={s.id}>
                      <Badge variant="secondary">{s.name}</Badge>
                    </li>
                  ))}
                </ul>
              )}
            </ItemCard>
          ))}
        </div>
      )}
      <ExperienceForm open={dialogs.formOpen} onOpenChange={dialogs.setFormOpen} experience={dialogs.editing} />
      <ConfirmDialog
        open={!!dialogs.deleting}
        onOpenChange={(o) => !o && dialogs.closeDelete()}
        title="¿Eliminar esta experiencia?"
        description={dialogs.deleting ? `Se eliminará «${dialogs.deleting.positionTitle} en ${dialogs.deleting.companyName}». Esta acción no se puede deshacer.` : undefined}
        confirmLabel="Eliminar"
        destructive
        pending={remove.isPending}
        onConfirm={() =>
          dialogs.deleting &&
          remove.mutate(dialogs.deleting.id, {
            onSuccess: () => {
              toast.success('Experiencia eliminada')
              dialogs.closeDelete()
            },
            onError: (err) => toast.error(errorMessage(err)),
          })
        }
      />
    </section>
  )
}
