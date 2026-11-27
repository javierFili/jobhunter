import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { FileText, Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { DataTable } from '@/components/DataTable'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { formatDateTime } from '@/lib/format'
import { errorMessage } from '@/lib/problem'
import { useDeleteResume, useDeleteUpload, useResumes, useUploads } from './api'
import { UploadDropzone } from './import/UploadDropzone'
import { resumeLanguageLabels } from './labels'
import { NewResumeDialog } from './NewResumeDialog'
import type { ResumeDto, UploadSummaryDto } from './types'

function ResumeList() {
  const { data, isLoading, error, refetch } = useResumes()
  const remove = useDeleteResume()
  const [creating, setCreating] = useState(false)
  const [deleting, setDeleting] = useState<ResumeDto | null>(null)
  return (
    <>
      <div className="mb-4 flex items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">Cada CV guarda un historial de versiones inmutables.</p>
        <Button onClick={() => setCreating(true)}>
          <Plus aria-hidden /> Nuevo CV
        </Button>
      </div>
      {isLoading ? (
        <LoadingState />
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : !data?.length ? (
        <EmptyState
          icon={FileText}
          title="Aún no tienes CV"
          description="Crea uno a partir de tu perfil verificado o importa tu CV actual."
          action={<Button onClick={() => setCreating(true)}>Nuevo CV</Button>}
        />
      ) : (
        <ul className="grid gap-3 md:grid-cols-2 lg:grid-cols-3">
          {data.map((r) => (
            <li key={r.id} className="flex flex-col gap-3 rounded-xl border bg-card p-4">
              <div className="flex items-start justify-between gap-2">
                <Link to={`/cvs/${r.id}`} className="font-semibold hover:underline">
                  {r.title}
                </Link>
                <Button variant="ghost" size="icon-sm" className="text-destructive" aria-label={`Eliminar ${r.title}`} onClick={() => setDeleting(r)}>
                  <Trash2 aria-hidden />
                </Button>
              </div>
              <div className="flex flex-wrap gap-1.5">
                <StatusBadge tone="info">{resumeLanguageLabels[r.language]}</StatusBadge>
                <StatusBadge>Plantilla {r.templateId === 'modern' ? 'moderna' : 'clásica'}</StatusBadge>
              </div>
              <p className="text-xs text-muted-foreground">Actualizado: {formatDateTime(r.updatedAt)}</p>
              <Button asChild variant="outline" size="sm" className="mt-auto">
                <Link to={`/cvs/${r.id}`}>Abrir editor</Link>
              </Button>
            </li>
          ))}
        </ul>
      )}
      <NewResumeDialog open={creating} onOpenChange={setCreating} />
      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(o) => !o && setDeleting(null)}
        title="¿Eliminar este CV?"
        description={deleting ? `Se eliminará «${deleting.title}» con todas sus versiones.` : undefined}
        confirmLabel="Eliminar"
        destructive
        pending={remove.isPending}
        onConfirm={() =>
          deleting &&
          remove.mutate(deleting.id, {
            onSuccess: () => {
              toast.success('CV eliminado')
              setDeleting(null)
            },
            onError: (e) => toast.error(errorMessage(e)),
          })
        }
      />
    </>
  )
}

function ImportTab() {
  const { data, isLoading, error, refetch } = useUploads()
  const remove = useDeleteUpload()
  return (
    <div className="grid gap-6">
      <UploadDropzone />
      <section aria-labelledby="uploads-title">
        <h2 id="uploads-title" className="mb-3 text-base font-semibold">
          CV subidos
        </h2>
        {isLoading ? (
          <LoadingState rows={2} />
        ) : error ? (
          <ErrorState error={error} onRetry={() => void refetch()} />
        ) : (
          <DataTable<UploadSummaryDto, number>
            caption="CV subidos"
            rows={data ?? []}
            getRowId={(r) => r.id}
            empty={<p className="text-sm text-muted-foreground">Todavía no subiste ningún CV.</p>}
            columns={[
              { id: 'created', header: 'Subido', cell: (r) => formatDateTime(r.createdAt) },
              { id: 'parser', header: 'Análisis', cell: (r) => (r.parser === 'AI' ? 'Con IA' : 'Automático') },
              {
                id: 'imported',
                header: 'Estado',
                cell: (r) =>
                  r.status === 'FAILED' ? (
                    <StatusBadge tone="danger">Error al procesar</StatusBadge>
                  ) : r.importedAt ? (
                    <StatusBadge tone="success">Importado</StatusBadge>
                  ) : (
                    <StatusBadge tone="warning">Pendiente de revisión</StatusBadge>
                  ),
              },
              {
                id: 'actions',
                header: <span className="sr-only">Acciones</span>,
                cell: (r) => (
                  <div className="flex justify-end gap-1">
                    <Button asChild variant="outline" size="sm">
                      <Link to={`/cvs/uploads/${r.id}`}>Revisar</Link>
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      className="text-destructive"
                      aria-label="Eliminar CV subido"
                      onClick={() => remove.mutate(r.id, { onError: (e) => toast.error(errorMessage(e)) })}
                    >
                      <Trash2 aria-hidden />
                    </Button>
                  </div>
                ),
              },
            ]}
          />
        )}
      </section>
    </div>
  )
}

export function ResumesPage() {
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab') === 'import' ? 'import' : 'resumes'
  return (
    <>
      <PageHeader title="Mis CV" description="Genera CV desde tu perfil verificado o importa uno existente para completar tu perfil." />
      <Tabs value={tab} onValueChange={(v) => setParams(v === 'import' ? { tab: v } : {}, { replace: true })}>
        <TabsList>
          <TabsTrigger value="resumes">Mis CV</TabsTrigger>
          <TabsTrigger value="import">Importar desde un CV</TabsTrigger>
        </TabsList>
        <TabsContent value="resumes" className="pt-4">
          <ResumeList />
        </TabsContent>
        <TabsContent value="import" className="pt-4">
          <ImportTab />
        </TabsContent>
      </Tabs>
    </>
  )
}
