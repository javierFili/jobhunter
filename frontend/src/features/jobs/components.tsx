import { BadgeCheck, CircleHelp, CircleX, ShieldCheck } from 'lucide-react'
import { ExternalLink } from '@/components/ExternalLink'
import { StatusBadge } from '@/components/StatusBadge'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import { eligibilityLabels, eligibilityTone } from './labels'
import type { Eligibility, JobReference, JobSourceRef } from './types'

const eligibilityIcon = { ELIGIBLE: BadgeCheck, LIKELY: ShieldCheck, UNKNOWN: CircleHelp, NOT_ELIGIBLE: CircleX }

const defaultEvidence: Record<Eligibility, string> = {
  ELIGIBLE: 'La oferta acepta candidatos desde Bolivia.',
  LIKELY: 'Por las condiciones de la oferta, probablemente acepta candidatos desde Bolivia.',
  UNKNOWN: 'No hay información suficiente: revisa la oferta original antes de postular.',
  NOT_ELIGIBLE: 'La oferta excluye a candidatos que residen en Bolivia.',
}

/** Very visible eligibility badge; UNKNOWN is never presented as eligible. */
export function EligibilityBadge({ value, evidence, className }: { value: Eligibility; evidence?: string | null; className?: string }) {
  const Icon = eligibilityIcon[value]
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span tabIndex={0} className="inline-flex rounded-4xl outline-none focus-visible:ring-2 focus-visible:ring-ring" data-eligibility={value}>
          <StatusBadge tone={eligibilityTone[value]} icon={<Icon aria-hidden />} className={cn('h-6 px-2.5 text-[0.8rem] font-semibold', className)}>
            {eligibilityLabels[value]}
          </StatusBadge>
        </span>
      </TooltipTrigger>
      <TooltipContent className="max-w-xs">{evidence || defaultEvidence[value]}</TooltipContent>
    </Tooltip>
  )
}

/** Source attribution required by the sources' terms. */
export function Attribution({ sources, references }: { sources?: JobSourceRef[]; references?: JobReference[] }) {
  if (references?.length) {
    return (
      <p className="text-xs text-muted-foreground">
        {references.map((r, i) => (
          <span key={`${r.sourceCode}-${i}`}>
            {i > 0 && ' · '}
            {r.sourceUrl ? (
              <ExternalLink href={r.sourceUrl} className="underline underline-offset-2 hover:text-foreground">
                {r.attributionText}
              </ExternalLink>
            ) : (
              <>{r.attributionText}</>
            )}
          </span>
        ))}
      </p>
    )
  }
  if (!sources?.length) return null
  return (
    <p className="text-xs text-muted-foreground">
      {sources.map((s, i) => (
        <span key={`${s.code}-${i}`}>
          {i > 0 && ' · '}
          {s.sourceUrl ? (
            <ExternalLink href={s.sourceUrl} className="underline underline-offset-2 hover:text-foreground">
              {s.attributionText}
            </ExternalLink>
          ) : (
            <>{s.attributionText}</>
          )}
        </span>
      ))}
    </p>
  )
}
