// Shared Zod schemas (form values ↔ API requests) for profile items.
// Used by the Mi perfil forms and by the CV import review screen.
import { z } from 'zod'
import type {
  CertificationRequest,
  EducationRequest,
  EducationStatus,
  EmploymentType,
  ExperienceRequest,
  ProjectKind,
  ProjectRequest,
  WorkModality,
} from '@/lib/types'
import { blankToNull, optionalDate, optionalHttpUrl, optionalText, requiredText } from '@/lib/zod'
import { endBeforeStart, linesToList } from './components/helpers'

const skillRefs = z.array(z.object({ id: z.number(), name: z.string() }))

export const experienceSchema = z
  .object({
    companyName: requiredText(150, 'Ingresa el nombre de la empresa'),
    companyUrl: optionalHttpUrl,
    positionTitle: requiredText(150, 'Ingresa el cargo'),
    employmentType: z.enum(['FULL_TIME', 'PART_TIME', 'CONTRACT', 'FREELANCE', 'INTERNSHIP'], {
      error: 'Elige el tipo de empleo',
    }),
    workModality: z.enum(['REMOTE', 'HYBRID', 'ONSITE'], { error: 'Elige la modalidad' }),
    location: optionalText(150),
    country: z.string(),
    startDate: z.string().min(1, 'Ingresa la fecha de inicio'),
    // Disabled inputs submit undefined in react-hook-form.
    endDate: z.string().optional(),
    current: z.boolean(),
    description: optionalText(4000),
    achievements: z.string().max(6000, 'Texto demasiado largo'),
    skills: skillRefs,
  })
  .superRefine((v, ctx) => {
    if (v.current && v.endDate) {
      ctx.addIssue({ code: 'custom', path: ['endDate'], message: 'Un empleo actual no tiene fecha de fin' })
    }
    if (endBeforeStart(v.startDate, v.endDate ?? '')) {
      ctx.addIssue({ code: 'custom', path: ['endDate'], message: 'La fecha de fin debe ser posterior a la de inicio' })
    }
  })

/** Form state: enums may be '' until the user picks one. */
export type ExperienceValues = Omit<z.infer<typeof experienceSchema>, 'employmentType' | 'workModality'> & {
  employmentType: EmploymentType | ''
  workModality: WorkModality | ''
}

export function experienceToRequest(v: z.infer<typeof experienceSchema>): ExperienceRequest {
  return {
    companyName: v.companyName.trim(),
    companyUrl: blankToNull(v.companyUrl),
    positionTitle: v.positionTitle.trim(),
    employmentType: v.employmentType,
    workModality: v.workModality,
    location: blankToNull(v.location),
    country: blankToNull(v.country),
    startDate: v.startDate,
    endDate: v.current ? null : blankToNull(v.endDate),
    current: v.current,
    description: blankToNull(v.description),
    achievements: linesToList(v.achievements),
    skillIds: v.skills.map((s) => s.id),
  }
}

export const projectSchema = z
  .object({
    kind: z.enum(['PERSONAL', 'ACADEMIC', 'FREELANCE_CLIENT', 'OPEN_SOURCE'], { error: 'Elige el tipo de proyecto' }),
    name: requiredText(150, 'Ingresa el nombre del proyecto'),
    role: optionalText(150),
    description: optionalText(4000),
    url: optionalHttpUrl,
    repoUrl: optionalHttpUrl,
    startDate: optionalDate,
    endDate: optionalDate,
    highlights: z.string().max(6000, 'Texto demasiado largo'),
    skills: skillRefs,
  })
  .refine((v) => !endBeforeStart(v.startDate, v.endDate), {
    path: ['endDate'],
    message: 'La fecha de fin debe ser posterior a la de inicio',
  })

export type ProjectValues = Omit<z.infer<typeof projectSchema>, 'kind'> & { kind: ProjectKind | '' }

export function projectToRequest(v: z.infer<typeof projectSchema>): ProjectRequest {
  return {
    kind: v.kind,
    name: v.name.trim(),
    role: blankToNull(v.role),
    description: blankToNull(v.description),
    url: blankToNull(v.url),
    repoUrl: blankToNull(v.repoUrl),
    startDate: blankToNull(v.startDate),
    endDate: blankToNull(v.endDate),
    highlights: linesToList(v.highlights),
    skillIds: v.skills.map((s) => s.id),
  }
}

export const educationSchema = z
  .object({
    institution: requiredText(200, 'Ingresa la institución'),
    degree: optionalText(200),
    fieldOfStudy: optionalText(200),
    startDate: optionalDate,
    endDate: optionalDate,
    status: z.enum(['COMPLETED', 'IN_PROGRESS', 'INCOMPLETE'], { error: 'Elige el estado' }),
    description: optionalText(4000),
  })
  .refine((v) => !endBeforeStart(v.startDate, v.endDate), {
    path: ['endDate'],
    message: 'La fecha de fin debe ser posterior a la de inicio',
  })

export type EducationValues = Omit<z.infer<typeof educationSchema>, 'status'> & { status: EducationStatus | '' }

export function educationToRequest(v: z.infer<typeof educationSchema>): EducationRequest {
  return {
    institution: v.institution.trim(),
    degree: blankToNull(v.degree),
    fieldOfStudy: blankToNull(v.fieldOfStudy),
    startDate: blankToNull(v.startDate),
    endDate: blankToNull(v.endDate),
    status: v.status,
    description: blankToNull(v.description),
  }
}

export const certificationSchema = z
  .object({
    name: requiredText(200, 'Ingresa el nombre de la certificación'),
    issuer: optionalText(200),
    issuedAt: optionalDate,
    expiresAt: optionalDate,
    credentialId: optionalText(200),
    credentialUrl: optionalHttpUrl,
  })
  .refine((v) => !endBeforeStart(v.issuedAt, v.expiresAt), {
    path: ['expiresAt'],
    message: 'El vencimiento debe ser posterior a la emisión',
  })

export type CertificationValues = z.infer<typeof certificationSchema>

export function certificationToRequest(v: CertificationValues): CertificationRequest {
  return {
    name: v.name.trim(),
    issuer: blankToNull(v.issuer),
    issuedAt: blankToNull(v.issuedAt),
    expiresAt: blankToNull(v.expiresAt),
    credentialId: blankToNull(v.credentialId),
    credentialUrl: blankToNull(v.credentialUrl),
  }
}

/** Server field names for cross-field rules → form field (contract: dateRangeValid / currentConsistent). */
export const crossFieldErrorMap = { dateRangeValid: 'endDate', currentConsistent: 'endDate' } as const
