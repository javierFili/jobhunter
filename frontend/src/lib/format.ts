const monthYear = new Intl.DateTimeFormat('es', { month: 'short', year: 'numeric', timeZone: 'UTC' })
const fullDate = new Intl.DateTimeFormat('es', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' })
const dateTime = new Intl.DateTimeFormat('es', { dateStyle: 'medium', timeStyle: 'short' })
const oneDecimal = new Intl.NumberFormat('es', { maximumFractionDigits: 1 })

/** Parses a `yyyy-MM-dd` string as a UTC date (no timezone shifting). */
export function parseLocalDate(value: string): Date {
  const [y, m, d] = value.split('-').map(Number)
  return new Date(Date.UTC(y, (m || 1) - 1, d || 1))
}

export function formatMonthYear(value: string | null | undefined): string {
  if (!value) return ''
  return monthYear.format(parseLocalDate(value))
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  return fullDate.format(parseLocalDate(value))
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  return dateTime.format(new Date(value))
}

export function formatPeriod(start: string | null, end: string | null, current = false): string {
  if (!start && !end) return 'Sin fechas'
  const from = start ? formatMonthYear(start) : '¿?'
  const to = current ? 'Actualidad' : end ? formatMonthYear(end) : 'Actualidad'
  return `${from} – ${to}`
}

export function formatYears(years: number): string {
  if (!years) return '0 años'
  const n = oneDecimal.format(years)
  return `${n} ${years === 1 ? 'año' : 'años'}`
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase())
    .join('')
}

const relative = new Intl.RelativeTimeFormat('es', { numeric: 'auto' })

/** "hace 3 días", "ayer", "hace 2 horas"… for ISO timestamps or yyyy-MM-dd. */
export function formatRelative(value: string | null | undefined, now: Date = new Date()): string {
  if (!value) return '—'
  const date = /^\d{4}-\d{2}-\d{2}$/.test(value) ? parseLocalDate(value) : new Date(value)
  const diffSec = Math.round((date.getTime() - now.getTime()) / 1000)
  const abs = Math.abs(diffSec)
  if (abs < 3600) return relative.format(Math.round(diffSec / 60), 'minute')
  if (abs < 86400) return relative.format(Math.round(diffSec / 3600), 'hour')
  if (abs < 86400 * 30) return relative.format(Math.round(diffSec / 86400), 'day')
  if (abs < 86400 * 365) return relative.format(Math.round(diffSec / (86400 * 30)), 'month')
  return relative.format(Math.round(diffSec / (86400 * 365)), 'year')
}

export function formatUsd(value: number | null | undefined, digits = 2): string {
  if (value == null) return '—'
  return new Intl.NumberFormat('es', { style: 'currency', currency: 'USD', maximumFractionDigits: digits }).format(value)
}
