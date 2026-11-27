import { useState } from 'react'
import { Languages, Loader2, Plus, Save, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { SelectField } from '@/components/SelectField'
import { VerifiedBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { LANGUAGE_CODES, languageLevelLabels, languageName, toOptions } from '@/lib/labels'
import { errorMessage } from '@/lib/problem'
import type { LanguageDto, LanguageLevel } from '@/lib/types'
import { useLanguages, useReplaceLanguages } from '../api'
import { TabToolbar } from '../components/TabToolbar'
import { useSelection } from '../components/useSelection'
import { useVerifyAction } from '../components/useVerifyAction'

interface Row {
  key: number
  languageCode: string
  level: LanguageLevel | ''
  certificate: string
  verified: boolean
}

const levelOptions = toOptions(languageLevelLabels)
let rowSeq = 0
const toRows = (list: LanguageDto[]): Row[] =>
  list.map((l) => ({ key: ++rowSeq, languageCode: l.languageCode, level: l.level, certificate: l.certificate ?? '', verified: l.verified }))

function LanguagesEditor({ initial }: { initial: LanguageDto[] }) {
  const [rows, setRows] = useState<Row[]>(() => toRows(initial))
  const [showErrors, setShowErrors] = useState(false)
  const replace = useReplaceLanguages()
  const selection = useSelection<string>()
  const verify = useVerifyAction('languageCodes', selection.clear)

  const used = rows.map((r) => r.languageCode)
  const languageOptions = (current: string) =>
    LANGUAGE_CODES.filter((c) => c === current || !used.includes(c)).map((c) => ({ value: c as string, label: languageName(c) }))
  const update = (key: number, patch: Partial<Row>) => setRows((rs) => rs.map((r) => (r.key === key ? { ...r, ...patch } : r)))
  const invalid = rows.some((r) => !r.languageCode || !r.level)
  const savedCodes = initial.map((l) => l.languageCode)

  const save = () => {
    if (invalid) {
      setShowErrors(true)
      return
    }
    replace.mutate(
      rows.map((r) => ({ languageCode: r.languageCode, level: r.level as LanguageLevel, certificate: r.certificate.trim() || null })),
      {
        onSuccess: () => {
          toast.success('Idiomas guardados')
          setShowErrors(false)
        },
        onError: (e) => toast.error(errorMessage(e)),
      },
    )
  }

  return (
    <>
      <TabToolbar
        description="Niveles según el Marco Común Europeo (MCER). Indica si tienes un certificado (TOEFL, IELTS, DELE…)."
        selectedCount={selection.selected.length}
        onVerify={() => verify.run(selection.selected)}
        verifying={verify.pending}
        extra={
          <Button
            variant="outline"
            onClick={() => setRows((rs) => [...rs, { key: ++rowSeq, languageCode: '', level: '', certificate: '', verified: false }])}
          >
            <Plus aria-hidden /> Agregar idioma
          </Button>
        }
      />
      {rows.length === 0 ? (
        <EmptyState icon={Languages} title="Aún no agregaste idiomas" description="Agrega al menos tu idioma nativo." />
      ) : (
        <ul className="grid gap-3">
          {rows.map((r, i) => {
            const persisted = savedCodes.includes(r.languageCode)
            return (
              <li key={r.key} className="grid items-end gap-3 rounded-xl border p-4 md:grid-cols-[auto_1fr_1fr_1fr_auto_auto]">
                <div className="flex h-9 items-center">
                  <Checkbox
                    aria-label={`Seleccionar ${r.languageCode ? languageName(r.languageCode) : `idioma ${i + 1}`}`}
                    disabled={!persisted}
                    checked={selection.isSelected(r.languageCode)}
                    onCheckedChange={(c) => selection.toggle(r.languageCode, c === true)}
                  />
                </div>
                <div className="grid gap-1.5">
                  <label htmlFor={`lang-code-${r.key}`} className="text-sm font-medium">
                    Idioma
                  </label>
                  <SelectField
                    id={`lang-code-${r.key}`}
                    value={r.languageCode}
                    onChange={(v) => update(r.key, { languageCode: v })}
                    options={languageOptions(r.languageCode)}
                    aria-invalid={showErrors && !r.languageCode ? true : undefined}
                  />
                </div>
                <div className="grid gap-1.5">
                  <label htmlFor={`lang-level-${r.key}`} className="text-sm font-medium">
                    Nivel
                  </label>
                  <SelectField
                    id={`lang-level-${r.key}`}
                    value={r.level}
                    onChange={(v) => update(r.key, { level: v })}
                    options={levelOptions}
                    aria-invalid={showErrors && !r.level ? true : undefined}
                  />
                </div>
                <div className="grid gap-1.5">
                  <label htmlFor={`lang-cert-${r.key}`} className="text-sm font-medium">
                    Certificado <span className="font-normal text-muted-foreground">(opcional)</span>
                  </label>
                  <Input id={`lang-cert-${r.key}`} value={r.certificate} onChange={(e) => update(r.key, { certificate: e.target.value })} />
                </div>
                <div className="flex h-9 items-center">{persisted && <VerifiedBadge verified={r.verified} />}</div>
                <Button
                  variant="ghost"
                  size="icon"
                  className="text-destructive"
                  aria-label={`Quitar ${r.languageCode ? languageName(r.languageCode) : 'idioma'}`}
                  onClick={() => setRows((rs) => rs.filter((x) => x.key !== r.key))}
                >
                  <Trash2 aria-hidden />
                </Button>
              </li>
            )
          })}
        </ul>
      )}
      {showErrors && invalid && (
        <p role="alert" className="mt-3 text-sm font-medium text-destructive">
          Completa el idioma y el nivel de cada fila.
        </p>
      )}
      <div className="mt-4 flex justify-end">
        <Button onClick={save} disabled={replace.isPending}>
          {replace.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <Save aria-hidden />}
          Guardar idiomas
        </Button>
      </div>
    </>
  )
}

export function LanguagesTab() {
  const { data, isLoading, error, refetch, dataUpdatedAt } = useLanguages()
  return (
    <section aria-labelledby="tab-lang-title">
      <h2 id="tab-lang-title" className="sr-only">
        Idiomas
      </h2>
      {isLoading ? <LoadingState rows={2} /> : error ? <ErrorState error={error} onRetry={() => void refetch()} /> : <LanguagesEditor key={dataUpdatedAt} initial={data ?? []} />}
    </section>
  )
}
