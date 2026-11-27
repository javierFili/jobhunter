import { ExternalLink, FolderGit2, Info } from 'lucide-react'
import { toast } from 'sonner'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { formatPeriod } from '@/lib/format'
import { errorMessage } from '@/lib/problem'
import type { ProjectDto } from '@/lib/types'
import { projectsApi } from '../api'
import { ItemCard } from '../components/ItemCard'
import { ProjectKindBadge } from '../components/ProjectKindBadge'
import { projectKindStyle } from '../components/projectKindStyle'
import { TabToolbar } from '../components/TabToolbar'
import { useEntityDialogs } from '../components/useEntityDialogs'
import { useSelection } from '../components/useSelection'
import { useVerifyAction } from '../components/useVerifyAction'
import { ProjectForm } from './ProjectForm'

export function ProjectsTab() {
  const { data, isLoading, error, refetch } = projectsApi.useList()
  const remove = projectsApi.useDelete()
  const dialogs = useEntityDialogs<ProjectDto>()
  const selection = useSelection<number>()
  const verify = useVerifyAction('projectIds', selection.clear)

  return (
    <section aria-labelledby="tab-projects-title">
      <h2 id="tab-projects-title" className="sr-only">
        Proyectos
      </h2>
      <div className="mb-4 flex items-start gap-2 rounded-lg border border-sky-200 bg-sky-50 px-3 py-2 text-sm text-sky-900 dark:border-sky-800 dark:bg-sky-950 dark:text-sky-100">
        <Info className="mt-0.5 size-4 shrink-0" aria-hidden />
        <p>
          Los proyectos <strong>no cuentan como experiencia laboral</strong>. Las habilidades usadas solo en proyectos se
          muestran como «Solo proyectos».
        </p>
      </div>
      <TabToolbar
        description="Proyectos personales, académicos, para clientes freelance u open source."
        addLabel="Agregar proyecto"
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
          icon={FolderGit2}
          title="Aún no agregaste proyectos"
          description="Muestra lo que construiste fuera de tus empleos: proyectos personales, académicos u open source."
          action={<Button onClick={dialogs.openCreate}>Agregar proyecto</Button>}
        />
      ) : (
        <div className="grid gap-3">
          {data.map((p) => (
            <ItemCard
              key={p.id}
              name={`Proyecto ${p.name}`}
              accent={projectKindStyle[p.kind].border}
              title={
                <span className="flex flex-col gap-1.5">
                  <ProjectKindBadge kind={p.kind} />
                  <span>{p.name}</span>
                </span>
              }
              subtitle={[`Proyecto${p.role ? ` · ${p.role}` : ''}`, formatPeriod(p.startDate, p.endDate)].join(' · ')}
              meta={
                <>
                  {p.url && (
                    <a href={p.url} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 hover:underline">
                      <ExternalLink className="size-3" aria-hidden /> Sitio
                    </a>
                  )}
                  {p.repoUrl && (
                    <a href={p.repoUrl} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 hover:underline">
                      <FolderGit2 className="size-3" aria-hidden /> Repositorio
                    </a>
                  )}
                </>
              }
              verified={p.verified}
              selected={selection.isSelected(p.id)}
              onSelectedChange={(on) => selection.toggle(p.id, on)}
              onEdit={() => dialogs.openEdit(p)}
              onDelete={() => dialogs.askDelete(p)}
            >
              {p.description && <p className="text-sm whitespace-pre-line">{p.description}</p>}
              {p.highlights.length > 0 && (
                <ul className="list-disc space-y-0.5 pl-5 text-sm">
                  {p.highlights.map((h, i) => (
                    <li key={i}>{h}</li>
                  ))}
                </ul>
              )}
              {p.skills.length > 0 && (
                <ul className="flex flex-wrap gap-1" aria-label="Habilidades">
                  {p.skills.map((s) => (
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
      <ProjectForm open={dialogs.formOpen} onOpenChange={dialogs.setFormOpen} project={dialogs.editing} />
      <ConfirmDialog
        open={!!dialogs.deleting}
        onOpenChange={(o) => !o && dialogs.closeDelete()}
        title="¿Eliminar este proyecto?"
        description={dialogs.deleting ? `Se eliminará «${dialogs.deleting.name}». Esta acción no se puede deshacer.` : undefined}
        confirmLabel="Eliminar"
        destructive
        pending={remove.isPending}
        onConfirm={() =>
          dialogs.deleting &&
          remove.mutate(dialogs.deleting.id, {
            onSuccess: () => {
              toast.success('Proyecto eliminado')
              dialogs.closeDelete()
            },
            onError: (err) => toast.error(errorMessage(err)),
          })
        }
      />
    </section>
  )
}
