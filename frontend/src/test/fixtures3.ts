// ⚠️ TEST DATA — fictitious Phase 5–7 fixtures used only by automated tests (MSW).
import type { ApplicationDto, BoardDto, PreparePackage, Transitions } from '@/features/applications/types'
import { STATUSES } from '@/features/applications/labels'
import type { CoverLetterDto, CoverLetterSummaryDto } from '@/features/coverletters/types'

export const app1: ApplicationDto = {
  id: 1,
  jobOfferId: 1,
  companyName: 'TEST DATA — Example Corp',
  positionTitle: 'TEST DATA Backend Engineer',
  jobUrl: 'https://example.test/jobs/1',
  sourceLabel: 'Remotive',
  status: 'READY_TO_APPLY',
  method: null,
  resumeId: null,
  resumeVersionNo: null,
  coverLetterId: null,
  salaryExpectation: null,
  salaryCurrency: null,
  salaryPeriod: null,
  appliedAt: null,
  followUpAt: '2020-01-01',
  notes: null,
  kanbanOrder: 0,
  match: { scoreTotal: 82, classification: 'HIGH_MATCH' },
  createdAt: '2026-11-20T10:00:00Z',
  updatedAt: '2026-11-21T10:00:00Z',
  version: 2,
}

export const app2: ApplicationDto = { ...app1, id: 2, jobOfferId: null, companyName: 'TEST DATA — Otra Empresa', positionTitle: 'TEST DATA Java Dev', status: 'FAVORITE', followUpAt: null, match: null, kanbanOrder: 0 }
export const app3: ApplicationDto = { ...app1, id: 3, positionTitle: 'TEST DATA Laravel Dev', status: 'FAVORITE', followUpAt: null, match: null, kanbanOrder: 1 }

export const transitions: Transitions = {
  DISCOVERED: ['ANALYZED', 'FAVORITE', 'WITHDRAWN'],
  ANALYZED: ['FAVORITE', 'CV_OPTIMIZED', 'WITHDRAWN'],
  FAVORITE: ['CV_OPTIMIZED', 'READY_TO_APPLY', 'WITHDRAWN'],
  CV_OPTIMIZED: ['READY_TO_APPLY', 'WITHDRAWN'],
  READY_TO_APPLY: ['WITHDRAWN'],
  APPLIED: ['RESPONSE_RECEIVED', 'REJECTED', 'WITHDRAWN'],
  RESPONSE_RECEIVED: ['INTERVIEW', 'REJECTED', 'WITHDRAWN'],
  INTERVIEW: ['OFFER', 'REJECTED', 'WITHDRAWN'],
  OFFER: ['WITHDRAWN'],
  REJECTED: [],
  WITHDRAWN: [],
}

export const board = (): BoardDto => ({
  columns: STATUSES.map((status) => ({ status, items: [app1, app2, app3].filter((a) => a.status === status) })),
})

export const preparePkg: PreparePackage = {
  applicationId: 1,
  method: 'EMAIL',
  applyUrl: 'https://example.test/apply/1',
  resume: { resumeId: 5, versionNo: 2, status: 'APPROVED', title: 'TEST DATA CV Backend' },
  coverLetter: { id: 40, format: 'EMAIL_INTRO', subject: 'TEST DATA asunto carta', body: 'TEST DATA cuerpo de la carta' },
  email: { subject: 'TEST DATA Postulación: Backend Engineer', body: 'TEST DATA Hola,\n\nAdjunto mi CV.' },
  checklist: [
    { key: 'resume', label: 'CV aprobado', done: true },
    { key: 'letter', label: 'Carta revisada', done: false },
  ],
  warnings: ['TEST DATA: la carta no está aprobada'],
}

export const letterSummary: CoverLetterSummaryDto = {
  id: 40,
  letterGroupId: 4,
  jobOfferId: 1,
  jobTitle: 'TEST DATA Backend Engineer',
  companyName: 'TEST DATA — Example Corp',
  format: 'LINKEDIN_MESSAGE',
  language: 'es',
  versionNo: 1,
  status: 'DRAFT',
  subject: null,
  preview: 'TEST DATA Hola, vi la oferta…',
  createdAt: '2026-11-26T10:00:00Z',
}

export const letter: CoverLetterDto = {
  id: 40,
  letterGroupId: 4,
  jobOfferId: 1,
  format: 'LINKEDIN_MESSAGE',
  language: 'es',
  tone: 'NEUTRAL',
  versionNo: 1,
  parentId: null,
  status: 'DRAFT',
  subject: null,
  body: 'TEST DATA Hola, vi la oferta de Backend. Tengo experiencia con Kubernetes en producción. Me encantaría conversar.',
  suggestions: [{ text: 'Tengo experiencia con Kubernetes en producción.', note: "Menciona 'Kubernetes', que no está en tu perfil verificado" }],
  validationReport: { groundedCount: 2, suggestedCount: 1, violations: [] },
  provider: 'DEEPSEEK',
  model: 'deepseek-flash',
  createdAt: '2026-11-26T10:00:00Z',
}
