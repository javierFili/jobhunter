export interface ReportViolation {
  rule: string
  section: string
  ref: string | null
  text: string
  message: string
}

export interface TruthReport {
  provider?: string | null
  model?: string | null
  groundedCount: number
  suggestedCount: number
  violations: ReportViolation[]
}

export const violationRuleLabels: Record<string, string> = {
  UNKNOWN_REF: 'Referencia desconocida',
  UNVERIFIED_TECHNOLOGY: 'Tecnología no verificada',
  UNSUPPORTED_NUMBER: 'Cifra sin respaldo',
  YEARS_EXCEEDED: 'Años de experiencia excedidos',
  PROJECT_AS_EMPLOYMENT: 'Proyecto presentado como empleo',
  NO_SOURCE: 'Sin fuente',
  DROPPED_ENTRY: 'Elemento omitido',
}

export const TRUTH_PRINCIPLE =
  'El optimizador nunca inventa hechos. Empresas, cargos, fechas, educación, certificaciones y niveles de idioma se copian de tu perfil verificado; la IA solo redacta el titular, el resumen, el orden y las viñetas, y cada viñeta cita el hecho que la respalda. Lo que no se puede verificar queda marcado como sugerencia y debe confirmarse o quitarse antes de aprobar.'
