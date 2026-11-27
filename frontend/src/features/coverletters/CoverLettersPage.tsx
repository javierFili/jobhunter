import { useState } from 'react'
import { Link } from 'react-router'
import { Mail } from 'lucide-react'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { Pagination } from '@/components/Pagination'
import { SelectField } from '@/components/SelectField'
import { StatusBadge } from '@/components/StatusBadge'
import { Label } from '@/components/ui/label'
import { resumeLanguageLabels } from '@/features/resumes/labels'
import { formatDateTime } from '@/lib/format'
import { toOptions } from '@/lib/labels'
import { useCoverLetters } from './api'
import { formatLabels } from './labels'
import type { LetterFormat, LetterStatus } from './types'

export function CoverLettersPage() {
  const [page, setPage] = useState(0)
  const [format, setFormat] = useState<LetterFormat | ''>('')
  const [status, setStatus] = useState<LetterStatus | ''>('')
  const { data, isLoading, error, refetch } = useCoverLetters({ format: format || null, status: status || null, page, size: 50 })
  const items = data?.content ?? []
  const filtered = !!format || !!status

  return (
    <>
      <PageHeader
        title="Cartas de presentación"
        description="Cartas y mensajes generados para cada oferta. Se genera desde el detalle de una oferta con «Generar carta»."
      />
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div className="grid w-56 gap-1.5">
          <Label htmlFor="cl-filter-format">Formato</Label>
          <SelectField id="cl-filter-format" value={format} onChange={(v) => { setFormat(v); setPage(0) }} options={toOptions(formatLabels)} noneLabel="Todos los formatos" />
        </div>
        <div className="grid w-44 gap-1.5">
          <Label htmlFor="cl-filter-status">Estado</Label>
          <SelectField
            id="cl-filter-status"
            value={status}
            onChange={(v) => {
              setStatus(v)
              setPage(0)
            }}
            noneLabel="Todos"
            options={[
              { value: 'DRAFT', label: 'Borrador' },
              { value: 'APPROVED', label: 'Aprobada' },
            ]}
          />
        </div>
      </div>
      {isLoading ? (
        <LoadingState />
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : !items.length ? (
        <EmptyState
          icon={Mail}
          title={filtered ? 'Ninguna carta coincide con los filtros' : 'Aún no tienes cartas'}
          description="Abre una oferta en «Descubrir empleos» y usa «Generar carta»."
        />
      ) : (
        <>
          <ul className="grid gap-3 md:grid-cols-2">
            {items.map((l) => (
              <li key={l.id}>
                <article aria-label={`Carta ${formatLabels[l.format]} para ${l.companyName ?? l.jobTitle ?? 'oferta'}`} className="grid h-full gap-2 rounded-xl border bg-card p-4">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div className="min-w-0">
                      <h3 className="font-semibold">
                        <Link to={`/cover-letters/${l.id}`} className="hover:underline">
                          {l.subject || `${formatLabels[l.format]} · ${l.companyName ?? ''}`}
                        </Link>
                      </h3>
                      <p className="text-sm text-muted-foreground">{[l.jobTitle, l.companyName].filter(Boolean).join(' · ')}</p>
                    </div>
                    <StatusBadge tone={l.status === 'APPROVED' ? 'success' : 'warning'}>{l.status === 'APPROVED' ? 'Aprobada' : 'Borrador'}</StatusBadge>
                  </div>
                  <div className="flex flex-wrap gap-1.5">
                    <StatusBadge tone="info">{formatLabels[l.format]}</StatusBadge>
                    <StatusBadge>{resumeLanguageLabels[l.language]}</StatusBadge>
                    <StatusBadge>v{l.versionNo}</StatusBadge>
                  </div>
                  {l.preview && <p className="line-clamp-3 text-sm text-muted-foreground">{l.preview}</p>}
                  <p className="mt-auto text-xs text-muted-foreground">{formatDateTime(l.createdAt)}</p>
                </article>
              </li>
            ))}
          </ul>
          {data && <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPageChange={setPage} />}
        </>
      )}
    </>
  )
}
