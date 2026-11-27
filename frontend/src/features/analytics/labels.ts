import type { BreakdownDimension } from './types'

export const dimensionLabels: Record<BreakdownDimension, string> = {
  technology: 'Tecnología',
  country: 'País',
  region: 'Región',
  source: 'Fuente',
  modality: 'Modalidad',
  salaryBand: 'Rango salarial',
  engagement: 'Forma de contratación',
  eligibility: 'Accesibilidad desde Bolivia',
}

export const funnelLabels: Record<string, string> = {
  CREATED: 'Creadas',
  APPLIED: 'Postuladas',
  RESPONSE_RECEIVED: 'Con respuesta',
  INTERVIEW: 'Entrevistas',
  OFFER: 'Ofertas',
  REJECTED: 'Rechazadas',
  WITHDRAWN: 'Retiradas',
}

export const PERIOD_OPTIONS = [
  { value: '7', label: 'Últimos 7 días' },
  { value: '30', label: 'Últimos 30 días' },
  { value: '90', label: 'Últimos 90 días' },
  { value: '365', label: 'Último año' },
]

export const priorityLabels = { HIGH: 'Prioridad alta', MEDIUM: 'Prioridad media', LOW: 'Prioridad baja' } as const

/** Last `days` days ending today, as yyyy-MM-dd (local). */
export function lastDays(days: number, now: Date = new Date()): { from: string; to: string } {
  const pad = (n: number) => String(n).padStart(2, '0')
  const iso = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  const from = new Date(now)
  from.setDate(from.getDate() - (days - 1))
  return { from: iso(from), to: iso(now) }
}

/** Categorical series colors (fixed order, never cycled); extra series fold into "Otros". */
export const SERIES_COLORS = ['var(--chart-1)', 'var(--chart-2)', 'var(--chart-3)', 'var(--chart-4)']
export const OTHER_COLOR = 'var(--chart-5)'

export const percent = (v: number | null | undefined, digits = 0) =>
  v == null ? '—' : new Intl.NumberFormat('es', { style: 'percent', maximumFractionDigits: digits }).format(v)
