export const queryKeys = {
  me: ['me'] as const,
  consents: ['me', 'consents'] as const,
  dashboard: ['dashboard', 'summary'] as const,
  profiles: ['profiles'] as const,
  experiences: ['experiences'] as const,
  projects: ['projects'] as const,
  educations: ['educations'] as const,
  certifications: ['certifications'] as const,
  skills: ['skills'] as const,
  catalogSkills: (q: string) => ['catalog', 'skills', q] as const,
  languages: ['languages'] as const,
  preferences: ['preferences'] as const,
}
