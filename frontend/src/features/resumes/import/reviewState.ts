import type { z } from 'zod'
import type { LanguageLevel } from '@/lib/types'
import type { CertificationValues, EducationValues, ExperienceValues, ProjectValues } from '@/features/profile/schemas'
import type { ParsedResume } from '../types'

export interface ReviewItem<V> {
  key: string
  selected: boolean
  values: V
  rawText: string | null
  errors: Record<string, string>
}

export interface LanguageValues {
  languageCode: string
  level: LanguageLevel | ''
  certificate: string
}

export interface ReviewState {
  experiences: ReviewItem<ExperienceValues>[]
  projects: ReviewItem<ProjectValues>[]
  educations: ReviewItem<EducationValues>[]
  certifications: ReviewItem<CertificationValues>[]
  skills: { skillId: number; name: string; selected: boolean }[]
  languages: ReviewItem<LanguageValues>[]
}

/** Accepts yyyy-MM-dd, yyyy-MM or yyyy; anything else becomes ''. */
export function normalizeDate(value: string | null | undefined): string {
  if (!value) return ''
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return value
  if (/^\d{4}-\d{2}$/.test(value)) return `${value}-01`
  if (/^\d{4}$/.test(value)) return `${value}-01-01`
  return ''
}

export function buildReviewState(parsed: ParsedResume | null): ReviewState {
  const skillNames = new Map((parsed?.skills ?? []).map((s) => [s.skillId, s.name]))
  const refs = (ids: number[] | null | undefined) => (ids ?? []).map((id) => ({ id, name: skillNames.get(id) ?? `Habilidad #${id}` }))
  const item = <V,>(prefix: string, i: number, values: V, rawText: string | null | undefined): ReviewItem<V> => ({
    key: `${prefix}-${i}`,
    selected: true,
    values,
    rawText: rawText ?? null,
    errors: {},
  })
  return {
    experiences: (parsed?.experiences ?? []).map((e, i) =>
      item<ExperienceValues>(
        'exp',
        i,
        {
          companyName: e.companyName ?? '',
          companyUrl: '',
          positionTitle: e.positionTitle ?? '',
          // The user must choose the employment type explicitly.
          employmentType: '',
          workModality: 'REMOTE',
          location: '',
          country: '',
          startDate: normalizeDate(e.startDate),
          endDate: e.current ? '' : normalizeDate(e.endDate),
          current: !!e.current,
          description: e.description ?? '',
          achievements: (e.achievements ?? []).join('\n'),
          skills: refs(e.skillIds),
        },
        e.rawText,
      ),
    ),
    projects: (parsed?.projects ?? []).map((p, i) =>
      item<ProjectValues>(
        'prj',
        i,
        {
          kind: 'PERSONAL',
          name: p.name ?? '',
          role: '',
          description: p.description ?? '',
          url: '',
          repoUrl: '',
          startDate: '',
          endDate: '',
          highlights: '',
          skills: refs(p.skillIds),
        },
        p.rawText,
      ),
    ),
    educations: (parsed?.educations ?? []).map((e, i) =>
      item<EducationValues>(
        'edu',
        i,
        {
          institution: e.institution ?? '',
          degree: e.degree ?? '',
          fieldOfStudy: '',
          startDate: normalizeDate(e.startDate),
          endDate: normalizeDate(e.endDate),
          status: 'COMPLETED',
          description: '',
        },
        e.rawText,
      ),
    ),
    certifications: (parsed?.certifications ?? []).map((c, i) =>
      item<CertificationValues>(
        'cert',
        i,
        { name: c.name ?? '', issuer: c.issuer ?? '', issuedAt: normalizeDate(c.issuedAt), expiresAt: '', credentialId: '', credentialUrl: '' },
        c.rawText,
      ),
    ),
    skills: (parsed?.skills ?? []).map((s) => ({ ...s, selected: true })),
    languages: (parsed?.languages ?? []).map((l, i) =>
      item<LanguageValues>('lang', i, { languageCode: (l.languageCode ?? '').toLowerCase(), level: l.level ?? '', certificate: '' }, l.rawText),
    ),
  }
}

/** Validates selected items with a Zod schema; returns the parsed values or per-item errors. */
export function validateItems<V, O>(
  items: ReviewItem<V>[],
  schema: z.ZodType<O>,
): { ok: boolean; items: ReviewItem<V>[]; parsed: O[] } {
  let ok = true
  const parsed: O[] = []
  const next = items.map((it) => {
    if (!it.selected) return { ...it, errors: {} }
    const r = schema.safeParse(it.values)
    if (r.success) {
      parsed.push(r.data)
      return { ...it, errors: {} }
    }
    ok = false
    const errors: Record<string, string> = {}
    for (const issue of r.error.issues) {
      const k = String(issue.path[0] ?? '_')
      errors[k] ??= issue.message
    }
    return { ...it, errors }
  })
  return { ok, items: next, parsed }
}
