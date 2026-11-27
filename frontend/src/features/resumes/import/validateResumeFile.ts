import { ACCEPTED_UPLOAD_TYPES, MAX_UPLOAD_BYTES, uploadErrorMessages } from '../labels'

/** Client-side check (the server validates again). Returns an error message or null. */
export function validateResumeFile(file: File): string | null {
  const name = file.name.toLowerCase()
  const okType = file.type in ACCEPTED_UPLOAD_TYPES || name.endsWith('.pdf') || name.endsWith('.docx')
  if (!okType) return uploadErrorMessages.UNSUPPORTED_FILE
  if (file.size > MAX_UPLOAD_BYTES) return uploadErrorMessages.FILE_TOO_LARGE
  if (file.size === 0) return uploadErrorMessages.INVALID_FILE
  return null
}
