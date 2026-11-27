import { useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { ArrowLeft, ClipboardList, FileCog, Loader2, Mail, Sparkles, Bookmark, BookmarkCheck, Building2, ExternalLink as ExternalIcon, EyeOff, Eye, MapPin } from 'lucide-react'
import { toast } from 'sonner'
import { ErrorState } from '@/components/ErrorState'
import { ExternalLink } from '@/components/ExternalLink'
import { LoadingState } from '@/components/LoadingState'
import { StatusBadge } from '@/components/StatusBadge'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { formatDate, formatDateTime, formatRelative } from '@/lib/format'
import { countryName, languageName, seniorityLabels } from '@/lib/labels'
import { errorMessage } from '@/lib/problem'
import { usePrepareFromJob } from '@/features/applications/usePrepareFromJob'
import { GenerateLetterDialog } from '@/features/coverletters/GenerateLetterDialog'
import { OptimizeResumeDialog } from '@/features/resumes/OptimizeResumeDialog'
import { AnalysisCard } from '@/features/matching/AnalysisCard'
import { useAnalyzeJob } from '@/features/matching/api'
import { MatchCard } from '@/features/matching/MatchCard'
import { apiErrorCode, taskErrorMessage } from '@/lib/task'
import { useJob, useJobState } from './api'
import { Attribution, EligibilityBadge } from './components'
import { eligibilityLabels, jobContractLabels, jobEngagementLabels, jobModalityLabels, jobStatusLabels, remoteScopeLabels } from './labels'
import { formatSalary } from './salary'


function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-0.5">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="text-sm">{children}</dd>
    </div>
  )
}

export function JobDetailPage() {
  const id = Number(useParams().jobId)
  const { data: job, isLoading, error, refetch } = useJob(id)
  const state = useJobState()
  const analyze = useAnalyzeJob(id)
  const prepare = usePrepareFromJob(id)
  const [optimizeOpen, setOptimizeOpen] = useState(false)
  const [letterOpen, setLetterOpen] = useState(false)

  if (isLoading) return <LoadingState />
  if (error || !job) return <ErrorState error={error} onRetry={() => void refetch()} />

  const applyHref = job.applyUrl ?? job.references.find((r) => r.applyUrl)?.applyUrl ?? job.references.find((r) => r.sourceUrl)?.sourceUrl ?? job.canonicalUrl
  const salary = formatSalary(job)
  const toggle = (patch: { saved?: boolean; hidden?: boolean }, message: string) =>
    state.mutate({ id: job.id, ...patch }, { onSuccess: () => toast.success(message), onError: (e) => toast.error(errorMessage(e)) })

  return (
    <>
      <Button asChild variant="ghost" size="sm" className="mb-2 -ml-2">
        <Link to="/jobs">
          <ArrowLeft aria-hidden /> Volver a empleos
        </Link>
      </Button>
      <div className="mb-6 grid gap-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <h1 className="text-2xl font-semibold tracking-tight">{job.title}</h1>
            <p className="mt-1 flex flex-wrap gap-x-3 text-sm text-muted-foreground">
              {job.companyName && (
                <span className="inline-flex items-center gap-1">
                  <Building2 className="size-3.5" aria-hidden /> {job.companyName}
                </span>
              )}
              {(job.locationText || job.country) && (
                <span className="inline-flex items-center gap-1">
                  <MapPin className="size-3.5" aria-hidden /> {job.locationText ?? countryName(job.country!)}
                </span>
              )}
            </p>
          </div>
          <EligibilityBadge value={job.boliviaEligibility} evidence={job.eligibilityEvidence} />
        </div>
        <Attribution references={job.references} sources={job.sources} />
        <div className="flex flex-wrap gap-2">
          {applyHref && (
            <Button asChild>
              <ExternalLink href={applyHref}>
                <ExternalIcon aria-hidden /> Abrir oferta original
              </ExternalLink>
            </Button>
          )}
          <Button variant="outline" aria-pressed={job.saved} disabled={state.isPending} onClick={() => toggle({ saved: !job.saved }, job.saved ? 'Oferta quitada de guardadas' : 'Oferta guardada')}>
            {job.saved ? <BookmarkCheck aria-hidden /> : <Bookmark aria-hidden />}
            {job.saved ? 'Guardada' : 'Guardar'}
          </Button>
          <Button variant="outline" aria-pressed={job.hidden} disabled={state.isPending} onClick={() => toggle({ hidden: !job.hidden }, job.hidden ? 'Oferta visible de nuevo' : 'Oferta ocultada')}>
            {job.hidden ? <Eye aria-hidden /> : <EyeOff aria-hidden />}
            {job.hidden ? 'Mostrar' : 'Ocultar'}
          </Button>
          <Button
            variant="outline"
            disabled={analyze.isPending}
            onClick={() => {
              analyze.mutate(false, {
                onError: (e) => {
                  const code = apiErrorCode(e)
                  toast.error(code ? taskErrorMessage(code, errorMessage(e)) : errorMessage(e))
                },
              })
              document.getElementById('analisis-ia')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
            }}
          >
            <Sparkles aria-hidden /> Analizar
          </Button>
          <Button variant="outline" onClick={() => setOptimizeOpen(true)}>
            <FileCog aria-hidden /> Optimizar CV
          </Button>
          <Button variant="outline" onClick={() => setLetterOpen(true)}>
            <Mail aria-hidden /> Generar carta
          </Button>
          <Button variant="outline" onClick={() => void prepare.run()} disabled={prepare.pending}>
            {prepare.pending ? <Loader2 className="animate-spin" aria-hidden /> : <ClipboardList aria-hidden />}
            Preparar postulación
          </Button>
        </div>
      </div>

      <div className="mb-4 grid gap-4 xl:grid-cols-2">
        <MatchCard jobId={job.id} />
        <div id="analisis-ia" className="scroll-mt-20">
          <AnalysisCard jobId={job.id} />
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-[1fr_20rem]">
        <Card>
          <CardHeader>
            <CardTitle>
              <h2 className="text-base font-semibold">Descripción</h2>
            </CardTitle>
          </CardHeader>
          <CardContent>
            {job.descriptionHtml ? (
              // Sanitized server-side (contract); rendered inside a prose container.
              <div className="job-description" dangerouslySetInnerHTML={{ __html: job.descriptionHtml }} />
            ) : (
              <p className="text-sm whitespace-pre-line">{job.descriptionText ?? 'Sin descripción.'}</p>
            )}
          </CardContent>
        </Card>
        <div className="grid content-start gap-4">
          <Card>
            <CardHeader>
              <CardTitle>
                <h2 className="text-base font-semibold">Accesibilidad desde Bolivia</h2>
              </CardTitle>
            </CardHeader>
            <CardContent className="grid gap-2 text-sm">
              <p className="font-medium">{eligibilityLabels[job.boliviaEligibility]}</p>
              <p className="text-muted-foreground">{job.eligibilityEvidence ?? 'Sin evidencia explícita en la oferta.'}</p>
              {job.remoteCountries.length > 0 && <p>Países permitidos: {job.remoteCountries.map(countryName).join(', ')}</p>}
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>
                <h2 className="text-base font-semibold">Detalles</h2>
              </CardTitle>
            </CardHeader>
            <CardContent>
              <dl className="grid gap-3">
                {salary && <Fact label="Salario">{salary}</Fact>}
                {job.workModality && <Fact label="Modalidad">{jobModalityLabels[job.workModality]}</Fact>}
                {job.remoteScope && <Fact label="Alcance del remoto">{remoteScopeLabels[job.remoteScope]}</Fact>}
                {job.contractType && <Fact label="Contrato">{jobContractLabels[job.contractType]}</Fact>}
                {job.engagementType && <Fact label="Forma de contratación">{jobEngagementLabels[job.engagementType]}</Fact>}
                {job.seniority && <Fact label="Seniority">{seniorityLabels[job.seniority]}</Fact>}
                {job.requiredLanguages.length > 0 && (
                  <Fact label="Idiomas requeridos">
                    {job.requiredLanguages.map((l) => `${languageName(l.code)}${l.level ? ` (${l.level})` : ''}`).join(', ')}
                  </Fact>
                )}
                <Fact label="Publicada">
                  {job.publishedAt ? formatRelative(job.publishedAt) : '—'}
                  {job.publishedIsApprox && ' (aprox.)'}
                </Fact>
                {job.expiresAt && <Fact label="Vence">{formatDate(job.expiresAt.slice(0, 10))}</Fact>}
                <Fact label="Estado">
                  <StatusBadge tone={job.status === 'ACTIVE' ? 'success' : 'danger'}>{jobStatusLabels[job.status]}</StatusBadge>
                </Fact>
                {job.lastVerifiedAt && <Fact label="Última verificación">{formatDateTime(job.lastVerifiedAt)}</Fact>}
              </dl>
              {job.skills.length > 0 && (
                <ul className="mt-4 flex flex-wrap gap-1" aria-label="Habilidades requeridas">
                  {job.skills.map((s) => (
                    <li key={s.id}>
                      <Badge variant="secondary">{s.name}</Badge>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
          {job.references.length > 0 && (
            <Card>
              <CardHeader>
                <CardTitle>
                  <h2 className="text-base font-semibold">Fuentes</h2>
                </CardTitle>
              </CardHeader>
              <CardContent>
                <ul className="grid gap-2 text-sm">
                  {job.references.map((r, i) => (
                    <li key={`${r.sourceCode}-${i}`} className="grid gap-0.5">
                      {r.sourceUrl ? (
                        <ExternalLink href={r.sourceUrl} className="font-medium text-primary hover:underline">
                          Vía {r.attributionText}
                        </ExternalLink>
                      ) : (
                        <span className="font-medium">Vía {r.attributionText}</span>
                      )}
                      <span className="text-xs text-muted-foreground">
                        Vista por primera vez {formatRelative(r.firstSeenAt)}
                        {r.removedAt && ` · retirada ${formatRelative(r.removedAt)}`}
                      </span>
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>
          )}
        </div>
      </div>
      <OptimizeResumeDialog jobId={job.id} open={optimizeOpen} onOpenChange={setOptimizeOpen} />
      <GenerateLetterDialog jobId={job.id} open={letterOpen} onOpenChange={setLetterOpen} />
    </>
  )
}
