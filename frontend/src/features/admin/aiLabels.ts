import type { AiProvider, Effort, PromptKey } from './types'

export const promptKeyInfo: Record<PromptKey, { label: string; help: string }> = {
  JOB_ANALYSIS: { label: 'Análisis de ofertas', help: 'Extrae requisitos, idiomas y condiciones del texto de cada oferta. Solo recibe el texto público de la oferta.' },
  MATCH_SEMANTIC: { label: 'Evaluación semántica', help: 'Compara tu perfil verificado (sin datos de contacto) con la oferta para afinar la compatibilidad.' },
  CV_PARSE: { label: 'Estructurar CV', help: 'Convierte el texto de un CV subido en experiencias, proyectos y habilidades. Se eliminan correos, teléfonos y enlaces.' },
  CV_OPTIMIZE: { label: 'Optimizar CV (Fase 5)', help: 'Adaptará la redacción del CV a una oferta concreta, sin inventar datos.' },
  COVER_LETTER: { label: 'Cartas (Fase 5)', help: 'Generará cartas de presentación a partir de tu perfil verificado.' },
}

export const providerLabels: Record<AiProvider, string> = { DEEPSEEK: 'DeepSeek', CLAUDE: 'Claude' }

export const effortLabels: Record<Effort, string> = { LOW: 'Bajo', MEDIUM: 'Medio', HIGH: 'Alto', XHIGH: 'Muy alto', MAX: 'Máximo' }

export const AI_PRIVACY_NOTE =
  'DeepSeek almacena datos en China según su política de privacidad; Claude es de Anthropic (EE. UU.). Los datos de contacto nunca se envían.'

/** First day of the current month and today, as yyyy-MM-dd (local time). */
export function currentMonthRange(now: Date = new Date()): { from: string; to: string } {
  const pad = (n: number) => String(n).padStart(2, '0')
  const y = now.getFullYear()
  const m = pad(now.getMonth() + 1)
  return { from: `${y}-${m}-01`, to: `${y}-${m}-${pad(now.getDate())}` }
}
