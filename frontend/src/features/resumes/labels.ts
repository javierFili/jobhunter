import type { BadgeTone } from '@/components/StatusBadge'
import type { BulletStatus, DiffSection, ResumeLanguage, VersionOrigin, VersionStatus } from './types'

export const resumeLanguageLabels: Record<ResumeLanguage, string> = {
  es: 'Español',
  en: 'Inglés',
  it: 'Italiano',
  de: 'Alemán',
  fr: 'Francés',
}

export const versionOriginLabels: Record<VersionOrigin, string> = {
  UPLOAD: 'CV subido',
  PROFILE: 'Desde perfil',
  MANUAL: 'Edición manual',
  OPTIMIZED: 'Optimizado',
  TRANSLATED: 'Traducido',
}

export const versionOriginTone: Record<VersionOrigin, BadgeTone> = {
  UPLOAD: 'neutral',
  PROFILE: 'info',
  MANUAL: 'violet',
  OPTIMIZED: 'teal',
  TRANSLATED: 'teal',
}

export const versionStatusLabels: Record<VersionStatus, string> = {
  DRAFT: 'Borrador',
  APPROVED: 'Aprobado',
  ARCHIVED: 'Archivado',
}

export const versionStatusTone: Record<VersionStatus, BadgeTone> = {
  DRAFT: 'warning',
  APPROVED: 'success',
  ARCHIVED: 'neutral',
}

export const bulletStatusLabels: Record<BulletStatus, string> = {
  GROUNDED: 'Basado en tu perfil',
  SUGGESTED: 'Sugerencia sin confirmar',
  USER_EDITED: 'Editado por ti',
}

export const diffSectionLabels: Record<DiffSection, string> = {
  basics: 'Datos básicos',
  summary: 'Resumen',
  experience: 'Experiencia',
  projects: 'Proyectos',
  education: 'Educación',
  certifications: 'Certificaciones',
  skills: 'Habilidades',
  languages: 'Idiomas',
}

export const MAX_UPLOAD_BYTES = 5 * 1024 * 1024
export const ACCEPTED_UPLOAD_TYPES = {
  'application/pdf': '.pdf',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': '.docx',
} as const

export const uploadErrorMessages: Record<string, string> = {
  INVALID_FILE: 'El archivo está dañado o no es un PDF/DOCX válido.',
  NO_TEXT: 'No se encontró texto en el archivo (¿es un PDF escaneado?). Sube una versión con texto seleccionable.',
  ENCRYPTED_FILE: 'El archivo está protegido con contraseña. Quita la protección y vuelve a subirlo.',
  TOO_MANY_PAGES: 'El CV tiene demasiadas páginas.',
  FILE_TOO_LARGE: 'El archivo supera el máximo de 5 MB.',
  UNSUPPORTED_FILE: 'Formato no soportado. Sube un archivo PDF o DOCX.',
}
