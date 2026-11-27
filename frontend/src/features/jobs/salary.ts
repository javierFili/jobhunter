import { salaryPeriodLabels } from '@/lib/labels'
import type { JobSummaryDto } from './types'

const money = (n: number, currency: string | null) => {
  try {
    return new Intl.NumberFormat('es', { style: currency ? 'currency' : 'decimal', currency: currency ?? undefined, maximumFractionDigits: 0 }).format(n)
  } catch {
    return `${n} ${currency ?? ''}`.trim()
  }
}

export function formatSalary(j: Pick<JobSummaryDto, 'salaryMin' | 'salaryMax' | 'salaryCurrency' | 'salaryPeriod' | 'salaryText' | 'salaryIsEstimated'>): string | null {
  let text: string | null = null
  if (j.salaryMin != null || j.salaryMax != null) {
    const range =
      j.salaryMin != null && j.salaryMax != null && j.salaryMin !== j.salaryMax
        ? `${money(j.salaryMin, j.salaryCurrency)} – ${money(j.salaryMax, j.salaryCurrency)}`
        : money((j.salaryMin ?? j.salaryMax)!, j.salaryCurrency)
    text = j.salaryPeriod ? `${range} · ${salaryPeriodLabels[j.salaryPeriod].toLowerCase()}` : range
  } else if (j.salaryText) {
    text = j.salaryText
  }
  if (text && j.salaryIsEstimated) text += ' (estimado)'
  return text
}
