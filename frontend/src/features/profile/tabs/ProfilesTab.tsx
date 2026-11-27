import { useState } from 'react'
import { ExternalLink, ListChecks, Star, UserSquare2 } from 'lucide-react'
import { toast } from 'sonner'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { countryName } from '@/lib/labels'
import { errorMessage } from '@/lib/problem'
import type { ProfileDto } from '@/lib/types'
import { profilesApi } from '../api'
import { ItemCard } from '../components/ItemCard'
import { TabToolbar } from '../components/TabToolbar'
import { useEntityDialogs } from '../components/useEntityDialogs'
import { ProfileForm } from './ProfileForm'
import { ProfileItemsDialog } from './ProfileItemsDialog'

export function ProfilesTab() {
  const { data, isLoading, error, refetch } = profilesApi.useList()
  const remove = profilesApi.useDelete()
  const dialogs = useEntityDialogs<ProfileDto>()
  const [itemsFor, setItemsFor] = useState<ProfileDto | null>(null)

  return (
    <section aria-labelledby="tab-profiles-title">
      <h2 id="tab-profiles-title" className="sr-only">
        Perfiles profesionales
      </h2>
      <TabToolbar
        description="Crea perfiles orientados a distintos puestos y elige qué experiencias, proyectos y habilidades incluye cada uno."
        addLabel="Nuevo perfil"
        onAdd={dialogs.openCreate}
      />
      {isLoading ? (
        <LoadingState />
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : !data?.length ? (
        <EmptyState
          icon={UserSquare2}
          title="Aún no tienes perfiles profesionales"
          description="Empieza con uno general; luego podrás crear variantes por tipo de puesto."
          action={<Button onClick={dialogs.openCreate}>Crear mi primer perfil</Button>}
        />
      ) : (
        <div className="grid gap-3 lg:grid-cols-2">
          {data.map((p) => {
            const links = [
              ['GitHub', p.githubUrl],
              ['LinkedIn', p.linkedinUrl],
              ['Portafolio', p.portfolioUrl],
              ['Sitio web', p.websiteUrl],
            ].filter(([, url]) => !!url) as [string, string][]
            return (
              <ItemCard
                key={p.id}
                name={`Perfil ${p.name}`}
                title={p.name}
                subtitle={p.headline || <span className="italic">Sin titular</span>}
                badges={
                  p.isDefault && (
                    <StatusBadge tone="info" icon={<Star aria-hidden />}>
                      Predeterminado
                    </StatusBadge>
                  )
                }
                meta={
                  <>
                    {p.targetRole && <span>Objetivo: {p.targetRole}</span>}
                    {(p.city || p.country) && <span>{[p.city, p.country && countryName(p.country)].filter(Boolean).join(', ')}</span>}
                    <span>
                      {p.experienceIds.length} experiencias · {p.projectIds.length} proyectos · {p.skillIds.length} habilidades
                    </span>
                  </>
                }
                onEdit={() => dialogs.openEdit(p)}
                onDelete={() => dialogs.askDelete(p)}
              >
                {p.summary && <p className="line-clamp-3 text-sm">{p.summary}</p>}
                {links.length > 0 && (
                  <div className="flex flex-wrap gap-3 text-xs">
                    {links.map(([label, url]) => (
                      <a key={label} href={url} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-primary hover:underline">
                        <ExternalLink className="size-3" aria-hidden /> {label}
                      </a>
                    ))}
                  </div>
                )}
                <Button variant="outline" size="sm" onClick={() => setItemsFor(p)}>
                  <ListChecks aria-hidden /> Elegir y ordenar elementos
                </Button>
              </ItemCard>
            )
          })}
        </div>
      )}
      <ProfileForm open={dialogs.formOpen} onOpenChange={dialogs.setFormOpen} profile={dialogs.editing} isFirst={!data?.length} />
      <ProfileItemsDialog profile={itemsFor} onOpenChange={(o) => !o && setItemsFor(null)} />
      <ConfirmDialog
        open={!!dialogs.deleting}
        onOpenChange={(o) => !o && dialogs.closeDelete()}
        title="¿Eliminar este perfil?"
        description={
          dialogs.deleting
            ? `Se eliminará el perfil «${dialogs.deleting.name}». Tus experiencias, proyectos y habilidades no se borran.`
            : undefined
        }
        confirmLabel="Eliminar"
        destructive
        pending={remove.isPending}
        onConfirm={() =>
          dialogs.deleting &&
          remove.mutate(dialogs.deleting.id, {
            onSuccess: () => {
              toast.success('Perfil eliminado')
              dialogs.closeDelete()
            },
            onError: (e) => toast.error(errorMessage(e)),
          })
        }
      />
    </section>
  )
}
