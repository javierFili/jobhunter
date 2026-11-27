import type { TruthReport } from '@/lib/truth'

export type LetterFormat = 'FORMAL_LETTER' | 'SHORT_MESSAGE' | 'EMAIL_INTRO' | 'FREELANCE_PROPOSAL' | 'LINKEDIN_MESSAGE'
export type LetterTone = 'FORMAL' | 'NEUTRAL' | 'FRIENDLY'
export type LetterLanguage = 'es' | 'en' | 'it' | 'de' | 'fr'
export type LetterStatus = 'DRAFT' | 'APPROVED'

export interface CoverLetterSummaryDto {
  id: number
  letterGroupId: number
  jobOfferId: number | null
  jobTitle: string | null
  companyName: string | null
  format: LetterFormat
  language: LetterLanguage
  versionNo: number
  status: LetterStatus
  subject: string | null
  preview: string | null
  createdAt: string
}

export interface CoverLetterDto {
  id: number
  letterGroupId: number
  jobOfferId: number | null
  format: LetterFormat
  language: LetterLanguage
  tone: LetterTone | null
  versionNo: number
  parentId: number | null
  status: LetterStatus
  subject: string | null
  body: string
  suggestions: { text: string; note: string | null }[]
  validationReport: TruthReport | null
  provider: string | null
  model: string | null
  createdAt: string
}
