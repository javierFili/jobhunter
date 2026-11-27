import type { LetterFormat, LetterTone } from './types'

export const formatLabels: Record<LetterFormat, string> = {
  FORMAL_LETTER: 'Carta formal',
  SHORT_MESSAGE: 'Mensaje corto',
  EMAIL_INTRO: 'Correo de presentación',
  FREELANCE_PROPOSAL: 'Propuesta freelance',
  LINKEDIN_MESSAGE: 'Mensaje de LinkedIn',
}

export const toneLabels: Record<LetterTone, string> = { FORMAL: 'Formal', NEUTRAL: 'Neutral', FRIENDLY: 'Cercano' }

/** Formats that carry a subject line. */
export const HAS_SUBJECT: LetterFormat[] = ['FORMAL_LETTER', 'EMAIL_INTRO']

/** Recommended maximum length per format (characters). */
export const MAX_CHARS: Partial<Record<LetterFormat, number>> = { LINKEDIN_MESSAGE: 300, SHORT_MESSAGE: 800 }

/** Removes one sentence from a body and tidies whitespace. */
export function removeSentence(body: string, sentence: string): string {
  const idx = body.indexOf(sentence)
  if (idx < 0) return body
  return (body.slice(0, idx) + body.slice(idx + sentence.length))
    .replace(/[ \t]{2,}/g, ' ')
    .replace(/ +([.,;:!?])/g, '$1')
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}
