import type { BadgeTone } from '@/components/StatusBadge'
import type { AnalysisDto, Classification, Recommendation, ScoreDimension } from './types'

export const classificationLabels: Record<Classification, string> = {
  HIGH_MATCH: 'Alta coincidencia',
  MEDIUM_MATCH: 'Coincidencia media',
  LOW_MATCH: 'Baja coincidencia',
  NOT_ELIGIBLE: 'No elegible',
  NEEDS_REVIEW: 'Requiere revisión',
}

export const classificationTone: Record<Classification, BadgeTone> = {
  HIGH_MATCH: 'success',
  MEDIUM_MATCH: 'teal',
  LOW_MATCH: 'warning',
  NOT_ELIGIBLE: 'danger',
  NEEDS_REVIEW: 'neutral',
}

export const recommendationLabels: Record<Recommendation, string> = {
  APPLY: 'Postular',
  CONSIDER: 'Considerar',
  SKIP: 'Descartar',
  REVIEW: 'Revisar',
}

export const methodLabels = { DETERMINISTIC: 'Determinista', AI_ASSISTED: 'Asistido por IA' } as const

export const dimensionLabels: Record<ScoreDimension, string> = {
  skills: 'Habilidades',
  experience: 'Experiencia',
  location: 'Ubicación',
  language: 'Idioma',
  seniority: 'Seniority',
  compensation: 'Compensación',
  contract: 'Contrato',
}

/** Weights used by the backend scorer (shown in a tooltip). */
export const dimensionWeights: Record<ScoreDimension, number> = {
  skills: 30,
  experience: 20,
  location: 15,
  language: 10,
  seniority: 10,
  compensation: 10,
  contract: 5,
}

export const DIMENSIONS = Object.keys(dimensionWeights) as ScoreDimension[]

export const DEFAULT_RECOMMENDED: Classification[] = ['HIGH_MATCH', 'MEDIUM_MATCH', 'NEEDS_REVIEW']

export const MATCH_DISCLAIMER = 'La puntuación estima la compatibilidad con tu perfil verificado; no es una probabilidad de contratación.'

export const providerNames = { DEEPSEEK: 'DeepSeek', CLAUDE: 'Claude' } as const

export function analysisCaption(a: Pick<AnalysisDto, 'provider' | 'model' | 'costUsd'>): string {
  const cost = a.costUsd != null ? ` · US$${a.costUsd.toFixed(4).replace(/0+$/, '').replace(/\.$/, '')}` : ''
  return `Analizado con ${providerNames[a.provider]} ${a.model}${cost}`
}
