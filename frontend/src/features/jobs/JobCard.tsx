import { Link } from 'react-router'
import { Bookmark, BookmarkCheck, Building2, Eye, EyeOff, MapPin } from 'lucide-react'
import { toast } from 'sonner'
import { StatusBadge } from '@/components/StatusBadge'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { formatRelative } from '@/lib/format'
import { countryName } from '@/lib/labels'
import { errorMessage } from '@/lib/problem'
import { cn } from '@/lib/utils'
import { MatchScoreChip } from '@/features/matching/MatchScoreChip'
import { useJobState } from './api'
import { Attribution, EligibilityBadge } from './components'
import { jobContractLabels, jobModalityLabels, jobStatusLabels, remoteScopeLabels } from './labels'
import { formatSalary } from './salary'
import type { JobSummaryDto } from './types'

export function JobCard({ job }: { job: JobSummaryDto }) {
  const state = useJobState()
  const salary = formatSalary(job)
  const toggle = (patch: { saved?: boolean; hidden?: boolean }, message: string) =>
    state.mutate({ id: job.id, ...patch }, { onSuccess: () => toast.success(message), onError: (e) => toast.error(errorMessage(e)) })
  const location = [job.locationText, job.country && !job.locationText ? countryName(job.country) : null].filter(Boolean).join(' · ')

  return (
    <article aria-label={job.title} className={cn('grid gap-3 rounded-xl border bg-card p-4', job.hidden && 'opacity-60')}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="font-semibold leading-tight">
            <Link to={`/jobs/${job.id}`} className="hover:underline">
              {job.title}
            </Link>
          </h3>
          <p className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted-foreground">
            {job.companyName && (
              <span className="inline-flex items-center gap-1">
                <Building2 className="size-3.5" aria-hidden /> {job.companyName}
              </span>
            )}
            {location && (
              <span className="inline-flex items-center gap-1">
                <MapPin className="size-3.5" aria-hidden /> {location}
              </span>
            )}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-1.5">
          {job.match && <MatchScoreChip score={job.match.scoreTotal} classification={job.match.classification} />}
          <EligibilityBadge value={job.boliviaEligibility} />
        </div>
      </div>
      <div className="flex flex-wrap gap-1.5">
        {job.workModality && <StatusBadge tone="info">{jobModalityLabels[job.workModality]}</StatusBadge>}
        {job.remoteScope && job.workModality === 'REMOTE' && <StatusBadge>{remoteScopeLabels[job.remoteScope]}</StatusBadge>}
        {job.contractType && job.contractType !== 'UNKNOWN' && <StatusBadge>{jobContractLabels[job.contractType]}</StatusBadge>}
        {job.status !== 'ACTIVE' && <StatusBadge tone="danger">{jobStatusLabels[job.status]}</StatusBadge>}
      </div>
      <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm">
        {salary && <span className="font-medium">{salary}</span>}
        <span className="text-muted-foreground">
          Publicada {formatRelative(job.publishedAt ?? job.firstSeenAt)}
          {job.publishedIsApprox && ' (aprox.)'}
        </span>
      </div>
      {job.skills.length > 0 && (
        <ul className="flex flex-wrap gap-1" aria-label="Habilidades requeridas">
          {job.skills.slice(0, 10).map((s) => (
            <li key={s.id}>
              <Badge variant="secondary">{s.name}</Badge>
            </li>
          ))}
        </ul>
      )}
      <div className="flex flex-wrap items-center justify-between gap-2 border-t pt-3">
        <Attribution sources={job.sources} />
        <div className="flex gap-1">
          <Button
            variant="ghost"
            size="sm"
            aria-pressed={job.saved}
            disabled={state.isPending}
            onClick={() => toggle({ saved: !job.saved }, job.saved ? 'Oferta quitada de guardadas' : 'Oferta guardada')}
          >
            {job.saved ? <BookmarkCheck aria-hidden /> : <Bookmark aria-hidden />}
            {job.saved ? 'Guardada' : 'Guardar'}
          </Button>
          <Button
            variant="ghost"
            size="sm"
            aria-pressed={job.hidden}
            disabled={state.isPending}
            onClick={() => toggle({ hidden: !job.hidden }, job.hidden ? 'Oferta visible de nuevo' : 'Oferta ocultada')}
          >
            {job.hidden ? <Eye aria-hidden /> : <EyeOff aria-hidden />}
            {job.hidden ? 'Mostrar' : 'Ocultar'}
          </Button>
        </div>
      </div>
    </article>
  )
}
