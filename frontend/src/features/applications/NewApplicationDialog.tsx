import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { FormField } from '@/components/FormField'
import { SelectField } from '@/components/SelectField'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { FormDialog } from '@/features/profile/components/FormDialog'
import { toOptions } from '@/lib/labels'
import { errorMessage } from '@/lib/problem'
import { useCreateApplication } from './api'
import { methodLabels } from './labels'
import type { AppMethod } from './types'

const URL_RE = /^https?:\/\/\S+$/i

function NewApplicationForm({ onOpenChange }: { onOpenChange: (o: boolean) => void }) {
  const navigate = useNavigate()
  const create = useCreateApplication()
  const [f, setF] = useState({ companyName: '', positionTitle: '', jobUrl: '', sourceLabel: '', method: '' as AppMethod | '', notes: '' })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const set = (p: Partial<typeof f>) => setF((x) => ({ ...x, ...p }))

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const errs: Record<string, string> = {}
    if (!f.companyName.trim()) errs.companyName = 'Ingresa la empresa'
    if (!f.positionTitle.trim()) errs.positionTitle = 'Ingresa el puesto'
    if (f.jobUrl && !URL_RE.test(f.jobUrl.trim())) errs.jobUrl = 'Ingresa una URL válida que empiece con http:// o https://'
    setErrors(errs)
    if (Object.keys(errs).length) return
    create.mutate(
      {
        companyName: f.companyName.trim(),
        positionTitle: f.positionTitle.trim(),
        jobUrl: f.jobUrl.trim() || null,
        sourceLabel: f.sourceLabel.trim() || null,
        method: f.method || null,
        notes: f.notes.trim() || null,
      },
      {
        onSuccess: (a) => {
          toast.success('Postulación creada')
          onOpenChange(false)
          navigate(`/applications/${a.id}`)
        },
        onError: (err) => toast.error(errorMessage(err)),
      },
    )
  }

  return (
    <FormDialog open onOpenChange={onOpenChange} title="Nueva postulación manual" description="Para ofertas que no están en JobHunter (por ejemplo, un contacto directo)." onSubmit={submit} submitting={create.isPending} submitLabel="Crear">
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="na-company" label="Empresa" required error={errors.companyName}>
          <Input value={f.companyName} onChange={(e) => set({ companyName: e.target.value })} />
        </FormField>
        <FormField id="na-position" label="Puesto" required error={errors.positionTitle}>
          <Input value={f.positionTitle} onChange={(e) => set({ positionTitle: e.target.value })} />
        </FormField>
        <FormField id="na-url" label="Enlace de la oferta" error={errors.jobUrl}>
          <Input type="url" placeholder="https://" value={f.jobUrl} onChange={(e) => set({ jobUrl: e.target.value })} />
        </FormField>
        <FormField id="na-source" label="Origen" hint="Ej.: referido, evento, LinkedIn.">
          <Input value={f.sourceLabel} onChange={(e) => set({ sourceLabel: e.target.value })} />
        </FormField>
        <FormField id="na-method" label="Medio de postulación">
          <SelectField value={f.method} onChange={(m) => set({ method: m })} options={toOptions(methodLabels)} noneLabel="Sin definir" />
        </FormField>
      </div>
      <FormField id="na-notes" label="Notas">
        <Textarea rows={3} value={f.notes} onChange={(e) => set({ notes: e.target.value })} />
      </FormField>
    </FormDialog>
  )
}

export function NewApplicationDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  return open ? <NewApplicationForm onOpenChange={onOpenChange} /> : null
}
