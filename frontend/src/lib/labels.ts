// Spanish (neutral LatAm) display labels for backend enums.
import type {
  ContractType,
  EducationStatus,
  EmploymentType,
  EngagementType,
  EvidenceLevel,
  LanguageLevel,
  ProjectKind,
  Region,
  SalaryPeriod,
  Seniority,
  SkillCategory,
  WorkModality,
} from './types'

export const employmentTypeLabels: Record<EmploymentType, string> = {
  FULL_TIME: 'Tiempo completo',
  PART_TIME: 'Medio tiempo',
  CONTRACT: 'Contrato',
  FREELANCE: 'Freelance',
  INTERNSHIP: 'Pasantía',
}

export const workModalityLabels: Record<WorkModality, string> = {
  REMOTE: 'Remoto',
  HYBRID: 'Híbrido',
  ONSITE: 'Presencial',
}

export const projectKindLabels: Record<ProjectKind, string> = {
  PERSONAL: 'Personal',
  ACADEMIC: 'Académico',
  FREELANCE_CLIENT: 'Cliente freelance',
  OPEN_SOURCE: 'Open source',
}

export const educationStatusLabels: Record<EducationStatus, string> = {
  COMPLETED: 'Completado',
  IN_PROGRESS: 'En curso',
  INCOMPLETE: 'Incompleto',
}

export const evidenceLevelLabels: Record<EvidenceLevel, string> = {
  PROFESSIONAL: 'Experiencia profesional',
  PROJECT_ONLY: 'Solo proyectos',
  ACADEMIC: 'Académico',
  LEARNING: 'Aprendiendo',
}

export const evidenceLevelDescriptions: Record<EvidenceLevel, string> = {
  PROFESSIONAL: 'La usaste en un empleo o contrato remunerado.',
  PROJECT_ONLY: 'La usaste solo en proyectos personales, freelance u open source.',
  ACADEMIC: 'La usaste en la universidad o en cursos.',
  LEARNING: 'La estás aprendiendo actualmente.',
}

export const skillCategoryLabels: Record<SkillCategory, string> = {
  PROGRAMMING_LANGUAGE: 'Lenguaje de programación',
  FRAMEWORK: 'Framework',
  DATABASE: 'Base de datos',
  CLOUD: 'Nube',
  TOOL: 'Herramienta',
  METHODOLOGY: 'Metodología',
  OTHER: 'Otra',
}

export const languageLevelLabels: Record<LanguageLevel, string> = {
  A1: 'A1 · Principiante',
  A2: 'A2 · Básico',
  B1: 'B1 · Intermedio',
  B2: 'B2 · Intermedio alto',
  C1: 'C1 · Avanzado',
  C2: 'C2 · Dominio',
  NATIVE: 'Nativo',
}

export const regionLabels: Record<Region, string> = {
  LATAM: 'Latinoamérica',
  EUROPE: 'Europa',
  NORTH_AMERICA: 'Norteamérica',
  ASIA: 'Asia',
  AFRICA: 'África',
  OCEANIA: 'Oceanía',
  WORLDWIDE: 'Todo el mundo',
}

export const contractTypeLabels: Record<ContractType, string> = {
  FULL_TIME: 'Tiempo completo',
  PART_TIME: 'Medio tiempo',
  CONTRACT: 'Contrato',
  TEMPORARY: 'Temporal',
  FREELANCE: 'Freelance',
  INTERNSHIP: 'Pasantía',
}

export const engagementTypeLabels: Record<EngagementType, string> = {
  EMPLOYEE_LOCAL: 'Empleado local',
  EMPLOYEE_EOR: 'Empleado vía EOR',
  CONTRACTOR: 'Contratista independiente',
  FREELANCE_PROJECT: 'Proyecto freelance',
  SPONSORED_RELOCATION: 'Reubicación patrocinada',
}

export const salaryPeriodLabels: Record<SalaryPeriod, string> = {
  HOUR: 'Por hora',
  DAY: 'Por día',
  MONTH: 'Mensual',
  YEAR: 'Anual',
}

export const seniorityLabels: Record<Seniority, string> = {
  JUNIOR: 'Junior',
  MID: 'Semi-senior',
  SENIOR: 'Senior',
}

/** Turns a label record into options for selects. */
export function toOptions<K extends string>(labels: Record<K, string>): { value: K; label: string }[] {
  return (Object.keys(labels) as K[]).map((value) => ({ value, label: labels[value] }))
}

export const COUNTRY_CODES = [
  'BO', 'AR', 'BR', 'CL', 'CO', 'MX', 'PE', 'UY', 'EC', 'PY', 'VE', 'CR', 'PA', 'GT', 'DO',
  'ES', 'IT', 'DE', 'PT', 'NL', 'FR', 'GB', 'IE', 'BE', 'CH', 'AT', 'SE', 'PL',
  'US', 'CA', 'AU', 'NZ', 'JP', 'SG', 'AE', 'IL',
] as const

export const LANGUAGE_CODES = ['es', 'en', 'pt', 'it', 'de', 'fr', 'nl', 'qu', 'ay', 'gn', 'zh', 'ja', 'ru', 'ar'] as const

export const CURRENCIES = ['USD', 'EUR', 'BOB', 'ARS', 'BRL', 'CLP', 'COP', 'MXN', 'PEN', 'UYU', 'GBP', 'CAD'] as const

const regionNames = new Intl.DisplayNames(['es'], { type: 'region' })
const languageNames = new Intl.DisplayNames(['es'], { type: 'language' })

export function countryName(code: string): string {
  try {
    return regionNames.of(code.toUpperCase()) ?? code
  } catch {
    return code
  }
}

export function languageName(code: string): string {
  try {
    const name = languageNames.of(code.toLowerCase()) ?? code
    return name.charAt(0).toUpperCase() + name.slice(1)
  } catch {
    return code
  }
}
