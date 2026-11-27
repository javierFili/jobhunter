import type { SalaryPeriod } from '@/lib/types'
import type { Classification } from '@/features/matching/types'

export type AppStatus =
  | 'DISCOVERED'
  | 'ANALYZED'
  | 'FAVORITE'
  | 'CV_OPTIMIZED'
  | 'READY_TO_APPLY'
  | 'APPLIED'
  | 'RESPONSE_RECEIVED'
  | 'INTERVIEW'
  | 'OFFER'
  | 'REJECTED'
  | 'WITHDRAWN'

export type AppMethod = 'EMAIL' | 'EXTERNAL_FORM' | 'CAREER_PORTAL' | 'LINKEDIN' | 'FREELANCE_PLATFORM' | 'OTHER'

export interface ApplicationDto {
  id: number
  jobOfferId: number | null
  companyName: string
  positionTitle: string
  jobUrl: string | null
  sourceLabel: string | null
  status: AppStatus
  method: AppMethod | null
  resumeId: number | null
  resumeVersionNo: number | null
  coverLetterId: number | null
  salaryExpectation: number | null
  salaryCurrency: string | null
  salaryPeriod: SalaryPeriod | null
  appliedAt: string | null
  followUpAt: string | null
  notes: string | null
  kanbanOrder: number
  match: { scoreTotal: number; classification: Classification } | null
  createdAt: string
  updatedAt: string
  version: number
}

export type ApplicationPatch = Partial<
  Pick<
    ApplicationDto,
    | 'companyName'
    | 'positionTitle'
    | 'jobUrl'
    | 'method'
    | 'resumeId'
    | 'resumeVersionNo'
    | 'coverLetterId'
    | 'salaryExpectation'
    | 'salaryCurrency'
    | 'salaryPeriod'
    | 'followUpAt'
    | 'notes'
  >
> & { version: number }

export type Transitions = Partial<Record<AppStatus, AppStatus[]>>

export interface BoardDto {
  columns: { status: AppStatus; items: ApplicationDto[] }[]
}

export type EventType = 'STATUS_CHANGE' | 'NOTE' | 'SUBMISSION_CONFIRMED' | 'FOLLOW_UP' | 'MESSAGE_RECEIVED' | 'INTERVIEW_SCHEDULED' | 'DOCUMENT_ATTACHED'

export interface ApplicationEvent {
  id: number
  type: EventType
  fromStatus: AppStatus | null
  toStatus: AppStatus | null
  note: string | null
  occurredAt: string
}

export interface PreparePackage {
  applicationId: number
  method: AppMethod | null
  applyUrl: string | null
  resume: { resumeId: number; versionNo: number; status: string; title: string } | null
  coverLetter: { id: number; format: string; subject: string | null; body: string } | null
  email: { subject: string; body: string }
  checklist: { key: string; label: string; done: boolean }[]
  warnings: string[]
}
