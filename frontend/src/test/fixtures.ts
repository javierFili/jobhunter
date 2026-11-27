// ⚠️ TEST DATA — fictitious fixtures used only by the automated tests (MSW). Not real people or companies.
import type {
  CatalogSkill,
  ConsentDto,
  DashboardSummary,
  ExperienceDto,
  ProjectDto,
  UserDto,
  UserSkillDto,
} from '@/lib/types'

export const testUser: UserDto = {
  id: 1,
  email: 'test.user@example.test',
  fullName: 'TEST DATA Usuario Prueba',
  role: 'USER',
  locale: 'es-BO',
  timezone: 'America/La_Paz',
  theme: 'system',
}

export const catalogSkills: CatalogSkill[] = [
  { id: 101, name: 'TEST DATA Java', category: 'PROGRAMMING_LANGUAGE' },
  { id: 102, name: 'TEST DATA React', category: 'FRAMEWORK' },
  { id: 103, name: 'TEST DATA PostgreSQL', category: 'DATABASE' },
  { id: 104, name: 'TEST DATA Docker', category: 'TOOL' },
]

export const experiences: ExperienceDto[] = [
  {
    id: 1,
    companyName: 'TEST DATA — Example Corp',
    companyUrl: 'https://example.test',
    positionTitle: 'Backend Developer',
    employmentType: 'FULL_TIME',
    workModality: 'REMOTE',
    location: 'La Paz',
    country: 'BO',
    startDate: '2021-03-01',
    endDate: null,
    current: true,
    description: 'TEST DATA — descripción ficticia.',
    achievements: ['TEST DATA — logro ficticio'],
    skills: [{ id: 101, name: 'TEST DATA Java' }],
    verified: true,
    source: 'MANUAL',
    version: 0,
  },
]

export const projects: ProjectDto[] = [
  {
    id: 1,
    kind: 'PERSONAL',
    name: 'TEST DATA — Proyecto Personal',
    role: 'Autor',
    description: null,
    url: null,
    repoUrl: 'https://example.test/repo',
    startDate: '2022-01-01',
    endDate: null,
    highlights: [],
    skills: [{ id: 102, name: 'TEST DATA React' }],
    verified: false,
    version: 0,
  },
  {
    id: 2,
    kind: 'ACADEMIC',
    name: 'TEST DATA — Proyecto Académico',
    role: null,
    description: null,
    url: null,
    repoUrl: null,
    startDate: null,
    endDate: null,
    highlights: [],
    skills: [],
    verified: true,
    version: 0,
  },
  {
    id: 3,
    kind: 'FREELANCE_CLIENT',
    name: 'TEST DATA — Proyecto Cliente',
    role: 'Desarrollador',
    description: null,
    url: null,
    repoUrl: null,
    startDate: '2023-05-01',
    endDate: '2023-08-01',
    highlights: [],
    skills: [],
    verified: false,
    version: 0,
  },
  {
    id: 4,
    kind: 'OPEN_SOURCE',
    name: 'TEST DATA — Proyecto OSS',
    role: 'Contribuidor',
    description: null,
    url: null,
    repoUrl: null,
    startDate: null,
    endDate: null,
    highlights: [],
    skills: [],
    verified: false,
    version: 0,
  },
]

export const userSkills: UserSkillDto[] = [
  { skillId: 101, name: 'TEST DATA Java', category: 'PROGRAMMING_LANGUAGE', evidenceLevel: 'PROFESSIONAL', selfRating: 4, lastUsedYear: 2026, yearsProfessional: 3.5, verified: true },
  { skillId: 102, name: 'TEST DATA React', category: 'FRAMEWORK', evidenceLevel: 'PROJECT_ONLY', selfRating: 3, lastUsedYear: 2025, yearsProfessional: 0, verified: false },
  { skillId: 103, name: 'TEST DATA PostgreSQL', category: 'DATABASE', evidenceLevel: 'ACADEMIC', selfRating: null, lastUsedYear: null, yearsProfessional: 0, verified: false },
  { skillId: 104, name: 'TEST DATA Docker', category: 'TOOL', evidenceLevel: 'LEARNING', selfRating: null, lastUsedYear: null, yearsProfessional: 0, verified: false },
]

export const consents: ConsentDto[] = [
  { consentType: 'AI_PROCESS_PROFILE', provider: 'deepseek', granted: false, grantedAt: null },
  { consentType: 'AI_PROCESS_PROFILE', provider: 'claude', granted: true, grantedAt: '2026-01-10T12:00:00Z' },
  { consentType: 'AI_PROCESS_CV', provider: 'deepseek', granted: false, grantedAt: null },
  { consentType: 'AI_PROCESS_CV', provider: 'claude', granted: false, grantedAt: null },
]

export const dashboardSummary: DashboardSummary = {
  profileCompleteness: 40,
  missingSections: ['headline', 'languages'],
  counts: { profiles: 0, experiences: 1, projects: 4, educations: 0, certifications: 0, skills: 4, languages: 0 },
}
