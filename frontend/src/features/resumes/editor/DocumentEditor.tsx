import type { ReactNode } from 'react'
import { Info, Plus, Trash2, X } from 'lucide-react'
import { Link } from 'react-router'
import { StatusBadge } from '@/components/StatusBadge'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { languageName, projectKindLabels } from '@/lib/labels'
import type { ResumeDocument } from '../types'
import { BulletList } from './BulletList'

interface Props {
  doc: ResumeDocument
  onChange: (doc: ResumeDocument) => void
}

function Section({ title, children, empty }: { title: string; children: ReactNode; empty?: boolean }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h3 className="text-base font-semibold">{title}</h3>
        </CardTitle>
      </CardHeader>
      <CardContent className="grid gap-4">{empty ? <p className="text-sm text-muted-foreground">Sin elementos en esta versión.</p> : children}</CardContent>
    </Card>
  )
}

function TextInput({ id, label, value, onChange, type = 'text' }: { id: string; label: string; value: string | null; onChange: (v: string) => void; type?: string }) {
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} type={type} value={value ?? ''} onChange={(e) => onChange(e.target.value)} />
    </div>
  )
}

function RemoveButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <Button type="button" variant="ghost" size="icon-sm" className="text-destructive" aria-label={label} onClick={onClick}>
      <Trash2 aria-hidden />
    </Button>
  )
}

/** Structured editor for a ResumeDocument. It can edit and remove content, never invent new facts. */
export function DocumentEditor({ doc, onChange }: Props) {
  const patch = (p: Partial<ResumeDocument>) => onChange({ ...doc, ...p })
  const setAt = <K extends 'experience' | 'projects' | 'education' | 'certifications' | 'languages' | 'skills'>(
    key: K,
    i: number,
    value: Partial<ResumeDocument[K][number]>,
  ) => patch({ [key]: (doc[key] as object[]).map((x, j) => (j === i ? { ...x, ...value } : x)) } as Partial<ResumeDocument>)
  const removeAt = (key: 'experience' | 'projects' | 'education' | 'certifications' | 'languages' | 'skills', i: number) =>
    patch({ [key]: (doc[key] as object[]).filter((_, j) => j !== i) } as Partial<ResumeDocument>)

  return (
    <div className="grid gap-4">
      <p className="flex items-start gap-2 rounded-lg border border-sky-200 bg-sky-50 px-3 py-2 text-sm text-sky-900 dark:border-sky-800 dark:bg-sky-950 dark:text-sky-100">
        <Info className="mt-0.5 size-4 shrink-0" aria-hidden />
        <span>
          La fuente de verdad es <Link to="/profile" className="font-medium underline underline-offset-4">Mi perfil</Link>. Aquí
          puedes redactar y reordenar; para agregar experiencias, proyectos o habilidades nuevas, edítalos en tu perfil y crea
          un CV nuevo. Lo que edites a mano queda bajo tu responsabilidad.
        </span>
      </p>

      <Section title="Datos básicos">
        <div className="grid gap-3 sm:grid-cols-2">
          <TextInput id="b-name" label="Nombre completo" value={doc.basics.fullName} onChange={(v) => patch({ basics: { ...doc.basics, fullName: v } })} />
          <TextInput id="b-headline" label="Titular" value={doc.basics.headline} onChange={(v) => patch({ basics: { ...doc.basics, headline: v } })} />
          <TextInput id="b-email" label="Correo" type="email" value={doc.basics.email} onChange={(v) => patch({ basics: { ...doc.basics, email: v } })} />
          <TextInput id="b-phone" label="Teléfono" value={doc.basics.phone} onChange={(v) => patch({ basics: { ...doc.basics, phone: v } })} />
          <TextInput id="b-location" label="Ubicación" value={doc.basics.location} onChange={(v) => patch({ basics: { ...doc.basics, location: v } })} />
        </div>
        <div className="grid gap-2">
          <span className="text-sm font-medium">Enlaces</span>
          {doc.basics.links.map((l, i) => (
            <div key={i} className="grid grid-cols-[1fr_2fr_auto] items-end gap-2">
              <TextInput
                id={`b-link-label-${i}`}
                label="Etiqueta"
                value={l.label}
                onChange={(v) => patch({ basics: { ...doc.basics, links: doc.basics.links.map((x, j) => (j === i ? { ...x, label: v } : x)) } })}
              />
              <TextInput
                id={`b-link-url-${i}`}
                label="URL"
                type="url"
                value={l.url}
                onChange={(v) => patch({ basics: { ...doc.basics, links: doc.basics.links.map((x, j) => (j === i ? { ...x, url: v } : x)) } })}
              />
              <RemoveButton label={`Quitar enlace ${l.label || i + 1}`} onClick={() => patch({ basics: { ...doc.basics, links: doc.basics.links.filter((_, j) => j !== i) } })} />
            </div>
          ))}
          <div>
            <Button type="button" variant="outline" size="sm" onClick={() => patch({ basics: { ...doc.basics, links: [...doc.basics.links, { label: '', url: '' }] } })}>
              <Plus aria-hidden /> Agregar enlace
            </Button>
          </div>
        </div>
      </Section>

      <Section title="Resumen">
        <Textarea aria-label="Resumen" rows={4} value={doc.summary ?? ''} onChange={(e) => patch({ summary: e.target.value })} />
      </Section>

      <Section title="Experiencia" empty={doc.experience.length === 0}>
        {doc.experience.map((e, i) => {
          const owner = [e.title, e.company].filter(Boolean).join(' en ') || `experiencia ${i + 1}`
          return (
            <fieldset key={e.ref} className="grid gap-3 rounded-lg border p-3">
              <legend className="px-1 text-sm font-medium">{owner}</legend>
              <div className="flex justify-end">
                <RemoveButton label={`Quitar ${owner} de este CV`} onClick={() => removeAt('experience', i)} />
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <TextInput id={`e-${i}-title`} label="Cargo" value={e.title} onChange={(v) => setAt('experience', i, { title: v })} />
                <TextInput id={`e-${i}-company`} label="Empresa" value={e.company} onChange={(v) => setAt('experience', i, { company: v })} />
                <TextInput id={`e-${i}-location`} label="Ubicación" value={e.location} onChange={(v) => setAt('experience', i, { location: v })} />
                <div className="grid grid-cols-2 gap-2">
                  <TextInput id={`e-${i}-start`} label="Inicio" type="month" value={e.startDate} onChange={(v) => setAt('experience', i, { startDate: v })} />
                  {!e.current && <TextInput id={`e-${i}-end`} label="Fin" type="month" value={e.endDate} onChange={(v) => setAt('experience', i, { endDate: v })} />}
                </div>
              </div>
              <div className="flex items-center gap-2">
                <Checkbox id={`e-${i}-current`} checked={e.current} onCheckedChange={(c) => setAt('experience', i, { current: c === true, ...(c === true ? { endDate: null } : {}) })} />
                <Label htmlFor={`e-${i}-current`} className="font-normal">
                  Empleo actual
                </Label>
              </div>
              <BulletList owner={owner} bullets={e.bullets} onChange={(bullets) => setAt('experience', i, { bullets })} />
            </fieldset>
          )
        })}
      </Section>

      <Section title="Proyectos" empty={doc.projects.length === 0}>
        {doc.projects.map((p, i) => {
          const owner = p.name || `proyecto ${i + 1}`
          return (
            <fieldset key={p.ref} className="grid gap-3 rounded-lg border p-3">
              <legend className="px-1 text-sm font-medium">{owner}</legend>
              <div className="flex items-center justify-between">
                {p.kind && <StatusBadge tone="violet">Proyecto · {projectKindLabels[p.kind]}</StatusBadge>}
                <RemoveButton label={`Quitar ${owner} de este CV`} onClick={() => removeAt('projects', i)} />
              </div>
              <div className="grid gap-3 sm:grid-cols-3">
                <TextInput id={`p-${i}-name`} label="Nombre" value={p.name} onChange={(v) => setAt('projects', i, { name: v })} />
                <TextInput id={`p-${i}-role`} label="Rol" value={p.role} onChange={(v) => setAt('projects', i, { role: v })} />
                <TextInput id={`p-${i}-url`} label="URL" type="url" value={p.url} onChange={(v) => setAt('projects', i, { url: v })} />
              </div>
              <BulletList owner={owner} bullets={p.bullets} onChange={(bullets) => setAt('projects', i, { bullets })} />
            </fieldset>
          )
        })}
      </Section>

      <Section title="Educación" empty={doc.education.length === 0}>
        {doc.education.map((e, i) => (
          <fieldset key={e.ref} className="grid gap-3 rounded-lg border p-3 sm:grid-cols-2">
            <legend className="px-1 text-sm font-medium">{e.institution || `Formación ${i + 1}`}</legend>
            <TextInput id={`ed-${i}-inst`} label="Institución" value={e.institution} onChange={(v) => setAt('education', i, { institution: v })} />
            <TextInput id={`ed-${i}-degree`} label="Título" value={e.degree} onChange={(v) => setAt('education', i, { degree: v })} />
            <TextInput id={`ed-${i}-field`} label="Área" value={e.field} onChange={(v) => setAt('education', i, { field: v })} />
            <div className="grid grid-cols-[1fr_1fr_auto] items-end gap-2">
              <TextInput id={`ed-${i}-start`} label="Inicio" type="month" value={e.startDate} onChange={(v) => setAt('education', i, { startDate: v })} />
              <TextInput id={`ed-${i}-end`} label="Fin" type="month" value={e.endDate} onChange={(v) => setAt('education', i, { endDate: v })} />
              <RemoveButton label={`Quitar ${e.institution ?? 'formación'} de este CV`} onClick={() => removeAt('education', i)} />
            </div>
          </fieldset>
        ))}
      </Section>

      <Section title="Certificaciones" empty={doc.certifications.length === 0}>
        {doc.certifications.map((c, i) => (
          <div key={c.ref} className="grid items-end gap-2 sm:grid-cols-[2fr_2fr_1fr_auto]">
            <TextInput id={`c-${i}-name`} label="Nombre" value={c.name} onChange={(v) => setAt('certifications', i, { name: v })} />
            <TextInput id={`c-${i}-issuer`} label="Emisor" value={c.issuer} onChange={(v) => setAt('certifications', i, { issuer: v })} />
            <TextInput id={`c-${i}-date`} label="Fecha" type="month" value={c.date} onChange={(v) => setAt('certifications', i, { date: v })} />
            <RemoveButton label={`Quitar ${c.name ?? 'certificación'} de este CV`} onClick={() => removeAt('certifications', i)} />
          </div>
        ))}
      </Section>

      <Section title="Habilidades" empty={doc.skills.length === 0}>
        {doc.skills.map((g, i) => (
          <div key={i} className="grid gap-2 rounded-lg border p-3">
            <div className="flex items-end gap-2">
              <div className="flex-1">
                <TextInput id={`s-${i}-label`} label="Nombre del grupo" value={g.label} onChange={(v) => setAt('skills', i, { label: v })} />
              </div>
              <RemoveButton label={`Quitar grupo ${g.label}`} onClick={() => removeAt('skills', i)} />
            </div>
            <ul className="flex flex-wrap gap-1.5" aria-label={`Habilidades del grupo ${g.label}`}>
              {g.items.map((it) => (
                <li key={it.ref}>
                  <Badge variant="secondary" className="h-6 gap-1 pr-1">
                    {it.name}
                    <button
                      type="button"
                      onClick={() => setAt('skills', i, { items: g.items.filter((x) => x.ref !== it.ref) })}
                      aria-label={`Quitar ${it.name}`}
                      className="rounded-full p-0.5 hover:bg-foreground/10"
                    >
                      <X className="size-3" aria-hidden />
                    </button>
                  </Badge>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </Section>

      <Section title="Idiomas" empty={doc.languages.length === 0}>
        <ul className="grid gap-2">
          {doc.languages.map((l, i) => (
            <li key={l.ref} className="grid grid-cols-[1fr_1fr_auto] items-end gap-2">
              <span className="pb-2 text-sm font-medium">{languageName(l.code)}</span>
              <TextInput id={`l-${i}-level`} label="Nivel" value={l.level} onChange={(v) => setAt('languages', i, { level: v })} />
              <RemoveButton label={`Quitar ${languageName(l.code)} de este CV`} onClick={() => removeAt('languages', i)} />
            </li>
          ))}
        </ul>
      </Section>
    </div>
  )
}
