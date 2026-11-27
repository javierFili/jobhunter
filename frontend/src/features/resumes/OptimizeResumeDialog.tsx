import { useState } from 'react'
import { Link } from 'react-router'
import { CheckCircle2, GitCompare, Loader2, PenLine, ShieldCheck } from 'lucide-react'
import { toast } from 'sonner'
import { FormField } from '@/components/FormField'
import { SelectField } from '@/components/SelectField'
import { TaskStatus } from '@/components/TaskStatus'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { toOptions } from '@/lib/labels'
import { errorMessage } from '@/lib/problem'
import { apiErrorCode, taskErrorMessage } from '@/lib/task'
import { TRUTH_PRINCIPLE } from '@/lib/truth'
import { useOptimizeResume, useOptimizeStatus, useResumes } from './api'
import { resumeLanguageLabels } from './labels'
import type { ResumeLanguage } from './types'

export function OptimizeResumeDialog({ jobId, open, onOpenChange }: { jobId: number; open: boolean; onOpenChange: (o: boolean) => void }) {
  const resumes = useResumes(open)
  const [resumeId, setResumeId] = useState<string>('')
  const [language, setLanguage] = useState<ResumeLanguage | ''>('')
  const [started, setStarted] = useState(false)
  const selected = resumes.data?.find((r) => String(r.id) === resumeId)
  const effectiveLanguage = language || selected?.language || 'es'
  const optimize = useOptimizeResume(jobId)
  const status = useOptimizeStatus(jobId, started && resumeId ? Number(resumeId) : null)
  const task = status.data?.task
  const version = task?.state === 'SUCCEEDED' ? status.data?.version : null
  const running = optimize.isPending || task?.state === 'RUNNING'

  const start = () => {
    if (!resumeId) return
    optimize.mutate(
      { resumeId: Number(resumeId), language: effectiveLanguage },
      {
        onSuccess: () => setStarted(true),
        onError: (e) => {
          const code = apiErrorCode(e)
          toast.error(code ? taskErrorMessage(code, errorMessage(e)) : errorMessage(e))
        },
      },
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Optimizar CV para esta oferta</DialogTitle>
          <DialogDescription>Se crea una versión nueva del CV elegido; la versión original no cambia.</DialogDescription>
        </DialogHeader>
        <p className="flex items-start gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs text-emerald-950 dark:border-emerald-800 dark:bg-emerald-950 dark:text-emerald-100">
          <ShieldCheck className="mt-0.5 size-4 shrink-0" aria-hidden />
          {TRUTH_PRINCIPLE}
        </p>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="opt-resume" label="CV base" required>
            <SelectField
              value={resumeId}
              onChange={(v) => {
                setResumeId(v)
                setStarted(false)
              }}
              placeholder={resumes.data?.length ? 'Elige un CV…' : 'No tienes CV todavía'}
              options={(resumes.data ?? []).map((r) => ({ value: String(r.id), label: r.title }))}
              disabled={running}
            />
          </FormField>
          <FormField id="opt-language" label="Idioma">
            <SelectField value={effectiveLanguage} onChange={(v) => setLanguage(v)} options={toOptions(resumeLanguageLabels)} disabled={running} />
          </FormField>
        </div>
        {!resumes.isLoading && !resumes.data?.length && (
          <p className="text-sm text-muted-foreground">
            Primero crea un CV en <Link to="/cvs" className="text-primary underline">Mis CV</Link>.
          </p>
        )}
        <TaskStatus task={started ? task : null} runningText="Optimizando tu CV… puede tardar un minuto." />
        {version && selected && (
          <div role="status" className="grid gap-2 rounded-lg border border-emerald-300 bg-emerald-50 p-3 text-sm text-emerald-950 dark:border-emerald-800 dark:bg-emerald-950 dark:text-emerald-100">
            <p className="flex items-center gap-1.5 font-medium">
              <CheckCircle2 className="size-4" aria-hidden /> Versión v{version.versionNo} creada. Revisa las sugerencias antes de aprobarla.
            </p>
            <div className="flex flex-wrap gap-2">
              <Button asChild size="sm">
                <Link to={`/cvs/${selected.id}?v=${version.versionNo}`}>
                  <PenLine aria-hidden /> Abrir en el editor
                </Link>
              </Button>
              <Button asChild size="sm" variant="outline">
                <Link
                  to={`/cvs/${selected.id}?tab=compare&to=${version.versionNo}${version.parentVersionId != null ? `&fromId=${version.parentVersionId}` : ''}`}
                >
                  <GitCompare aria-hidden /> Comparar con la versión anterior
                </Link>
              </Button>
            </div>
          </div>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cerrar
          </Button>
          <Button onClick={start} disabled={!resumeId || running}>
            {running && <Loader2 className="animate-spin" aria-hidden />}
            {version ? 'Optimizar de nuevo' : 'Optimizar'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
