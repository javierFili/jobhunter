import type { BadgeTone } from '@/components/StatusBadge'
import type { AppMethod, AppStatus, EventType } from './types'

export const STATUSES: AppStatus[] = [
  'DISCOVERED',
  'ANALYZED',
  'FAVORITE',
  'CV_OPTIMIZED',
  'READY_TO_APPLY',
  'APPLIED',
  'RESPONSE_RECEIVED',
  'INTERVIEW',
  'OFFER',
  'REJECTED',
  'WITHDRAWN',
]

export const statusLabels: Record<AppStatus, string> = {
  DISCOVERED: 'Descubierta',
  ANALYZED: 'Analizada',
  FAVORITE: 'Favorita',
  CV_OPTIMIZED: 'CV optimizado',
  READY_TO_APPLY: 'Lista para postular',
  APPLIED: 'Postulada',
  RESPONSE_RECEIVED: 'Respuesta recibida',
  INTERVIEW: 'Entrevista',
  OFFER: 'Oferta',
  REJECTED: 'Rechazada',
  WITHDRAWN: 'Retirada',
}

export const statusTone: Record<AppStatus, BadgeTone> = {
  DISCOVERED: 'neutral',
  ANALYZED: 'neutral',
  FAVORITE: 'violet',
  CV_OPTIMIZED: 'info',
  READY_TO_APPLY: 'teal',
  APPLIED: 'info',
  RESPONSE_RECEIVED: 'teal',
  INTERVIEW: 'violet',
  OFFER: 'success',
  REJECTED: 'danger',
  WITHDRAWN: 'neutral',
}

/** Statuses from which "Marcar como enviada" is allowed. */
export const PRE_APPLIED: AppStatus[] = ['DISCOVERED', 'ANALYZED', 'FAVORITE', 'CV_OPTIMIZED', 'READY_TO_APPLY']

export const methodLabels: Record<AppMethod, string> = {
  EMAIL: 'Correo electrónico',
  EXTERNAL_FORM: 'Formulario externo',
  CAREER_PORTAL: 'Portal de empleo de la empresa',
  LINKEDIN: 'LinkedIn',
  FREELANCE_PLATFORM: 'Plataforma freelance',
  OTHER: 'Otro',
}

export const eventLabels: Record<EventType, string> = {
  STATUS_CHANGE: 'Cambio de estado',
  NOTE: 'Nota',
  SUBMISSION_CONFIRMED: 'Envío registrado',
  FOLLOW_UP: 'Seguimiento',
  MESSAGE_RECEIVED: 'Mensaje recibido',
  INTERVIEW_SCHEDULED: 'Entrevista agendada',
  DOCUMENT_ATTACHED: 'Documento adjunto',
}

export const NEVER_SENDS = 'JobHunter nunca envía nada por ti: envías tú y luego lo registras aquí.'

/** yyyy-MM-dd of today in local time. */
export function todayIso(now: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

export const isOverdue = (followUpAt: string | null, today = todayIso()) => !!followUpAt && followUpAt < today
