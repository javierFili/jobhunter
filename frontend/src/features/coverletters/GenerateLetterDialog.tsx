import { useState } from 'react'
import { Link } from 'react-router'
import { CheckCircle2, Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { FormField } from '@/components/FormField'
import { SelectField } from '@/components/SelectField'
import { TaskStatus } from '@/components/TaskStatus'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { resumeLanguageLabels } from '@/features/resumes/labels'
import { toOptions } from '@/lib/labels'
import { errorMessage } from '@/lib/problem'
import { apiErrorCode, taskErrorMessage } from '@/lib/task'
import { useCoverLetters, useGenerateLetter, useLetterTask } from './api'
import { formatLabels, toneLabels } from './labels'
import type { LetterFormat, LetterLanguage, LetterTone } from './types'

export function GenerateLetterDialog({ jobId, open, onOpenChange }: { jobId: number; open: boolean; onOpenChange: (o: boolean) => void }) {
  const [format, setFormat] = useState<LetterFormat>('FORMAL_LETTER')
  const [language, setLanguage] = useState<LetterLanguage>('es')
  const [tone, setTone] = useState<LetterTone | ''>('NEUTRAL')
  const [started, setStarted] = useState(false)
  const generate = useGenerateLetter(jobId)
  const task = useLetterTask(jobId, started)
  const done = started && task.data?.state === 'SUCCEEDED'
  const letters = useCoverLetters({ jobId, size: 5 }, done)
  const newest = done ? letters.data?.content[0] : undefined
  const running = generate.isPending || task.data?.state === 'RUNNING'

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Generar carta de presentación</DialogTitle>
          <DialogDescription>
            Se redacta con los datos verificados de tu perfil y la oferta. Las frases que no se pueden respaldar quedan como
            sugerencias para revisar.
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 sm:grid-cols-3">
          <FormField id="cl-format" label="Formato">
            <SelectField value={format} onChange={(v) => v && setFormat(v)} options={toOptions(formatLabels)} disabled={running} />
          </FormField>
          <FormField id="cl-language" label="Idioma">
            <SelectField value={language} onChange={(v) => v && setLanguage(v)} options={toOptions(resumeLanguageLabels)} disabled={running} />
          </FormField>
          <FormField id="cl-tone" label="Tono">
            <SelectField value={tone} onChange={setTone} options={toOptions(toneLabels)} noneLabel="Predeterminado" disabled={running} />
          </FormField>
        </div>
        <TaskStatus task={started ? task.data : null} runningText="Redactando la carta…" />
        {newest && (
          <div role="status" className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-emerald-300 bg-emerald-50 p-3 text-sm text-emerald-950 dark:border-emerald-800 dark:bg-emerald-950 dark:text-emerald-100">
            <span className="flex items-center gap-1.5 font-medium">
              <CheckCircle2 className="size-4" aria-hidden /> Carta lista para revisar
            </span>
            <Button asChild size="sm">
              <Link to={`/cover-letters/${newest.id}`}>Abrir carta</Link>
            </Button>
          </div>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cerrar
          </Button>
          <Button
            disabled={running}
            onClick={() =>
              generate.mutate(
                { format, language, tone: tone || null },
                {
                  onSuccess: () => setStarted(true),
                  onError: (e) => {
                    const code = apiErrorCode(e)
                    toast.error(code ? taskErrorMessage(code, errorMessage(e)) : errorMessage(e))
                  },
                },
              )
            }
          >
            {running && <Loader2 className="animate-spin" aria-hidden />}
            Generar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
