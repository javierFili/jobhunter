import { useState, type FormEvent } from 'react'
import { Loader2, Send } from 'lucide-react'
import { toast } from 'sonner'
import { FormField } from '@/components/FormField'
import { SelectField } from '@/components/SelectField'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { toOptions } from '@/lib/labels'
import { ApiError, errorMessage } from '@/lib/problem'
import { useMarkSubmitted } from './api'
import { NEVER_SENDS, methodLabels } from './labels'
import type { AppMethod, ApplicationDto } from './types'

function nowLocal(): string {
  const d = new Date()
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset())
  return d.toISOString().slice(0, 16)
}

function SubmittedForm({ application, onDone }: { application: ApplicationDto; onDone: (a: ApplicationDto | null) => void }) {
  const [method, setMethod] = useState<AppMethod | ''>(application.method ?? '')
  const [when, setWhen] = useState(nowLocal)
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const mark = useMarkSubmitted()

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!method) {
      setError('Indica cómo enviaste la postulación')
      return
    }
    setError(null)
    mark.mutate(
      { id: application.id, method, submittedAt: when ? new Date(when).toISOString() : undefined, ...(note.trim() ? { note: note.trim() } : {}) },
      {
        onSuccess: (a) => {
          toast.success('Postulación registrada como enviada')
          onDone(a)
        },
        onError: (err) =>
          toast.error(
            err instanceof ApiError && err.code === 'INVALID_TRANSITION' ? 'Esta postulación ya no está en un estado previo al envío.' : errorMessage(err),
          ),
      },
    )
  }

  return (
    <form onSubmit={submit} noValidate className="grid gap-4">
      <p className="rounded-lg border border-sky-200 bg-sky-50 px-3 py-2 text-sm font-medium text-sky-950 dark:border-sky-800 dark:bg-sky-950 dark:text-sky-100">{NEVER_SENDS}</p>
      <FormField id="ms-method" label="¿Cómo la enviaste?" required error={error ?? undefined}>
        <SelectField value={method} onChange={setMethod} options={toOptions(methodLabels)} placeholder="Elige el medio…" />
      </FormField>
      <FormField id="ms-when" label="Fecha y hora de envío">
        <Input type="datetime-local" value={when} onChange={(e) => setWhen(e.target.value)} />
      </FormField>
      <FormField id="ms-note" label="Nota (opcional)">
        <Textarea rows={2} value={note} onChange={(e) => setNote(e.target.value)} />
      </FormField>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={() => onDone(null)}>
          Cancelar
        </Button>
        <Button type="submit" disabled={mark.isPending}>
          {mark.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <Send aria-hidden />}
          Registrar envío
        </Button>
      </DialogFooter>
    </form>
  )
}

/** "Marcar como enviada": the user sends the application manually and records it here. */
export function MarkSubmittedDialog({ application, onClose }: { application: ApplicationDto | null; onClose: (updated: ApplicationDto | null) => void }) {
  return (
    <Dialog open={!!application} onOpenChange={(o) => !o && onClose(null)}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Marcar como enviada</DialogTitle>
          <DialogDescription>
            {application ? `${application.positionTitle} · ${application.companyName}` : ''}
          </DialogDescription>
        </DialogHeader>
        {application && <SubmittedForm key={application.id} application={application} onDone={onClose} />}
      </DialogContent>
    </Dialog>
  )
}
