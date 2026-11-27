import { z } from 'zod'
import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'
import { toast } from 'sonner'
import { ApiError, errorMessage } from './problem'

const HTTP_URL = /^https?:\/\/[^\s/$.?#].[^\s]*$/i

/** Optional free text: '' allowed. */
export const optionalText = (max = 255) => z.string().trim().max(max, `Máximo ${max} caracteres`)

export const requiredText = (max = 255, message = 'Este campo es obligatorio') =>
  z.string().trim().min(1, message).max(max, `Máximo ${max} caracteres`)

/** Optional http(s) URL; '' allowed. */
export const optionalHttpUrl = z
  .string()
  .trim()
  .max(500, 'Máximo 500 caracteres')
  .refine((v) => v === '' || HTTP_URL.test(v), 'Ingresa una URL válida que empiece con http:// o https://')

/** Optional `yyyy-MM-dd` date; '' allowed. */
export const optionalDate = z
  .string()
  .refine((v) => v === '' || /^\d{4}-\d{2}-\d{2}$/.test(v), 'Fecha inválida')

export const optionalEmail = z
  .string()
  .trim()
  .refine((v) => v === '' || z.email().safeParse(v).success, 'Correo electrónico inválido')

/** '' / whitespace → null, otherwise trimmed. */
export function blankToNull(value: string | null | undefined): string | null {
  if (value === undefined || value === null) return null
  const t = value.trim()
  return t === '' ? null : t
}

export function nullToBlank(value: string | null | undefined): string {
  return value ?? ''
}

/** Cross-field rule names used by the backend → the form field that shows the message. */
const DEFAULT_FIELD_MAP: Record<string, string> = { dateRangeValid: 'endDate', currentConsistent: 'endDate' }

/**
 * Shows the problem `detail` as a toast and maps `errors[]` onto form fields.
 * Fields not present in the form are still reported via the toast.
 */
export function handleFormError<T extends FieldValues>(
  error: unknown,
  setError?: UseFormSetError<T>,
  fieldMap: Record<string, Path<T>> = {},
): void {
  if (error instanceof ApiError && setError) {
    const map: Record<string, string> = { ...DEFAULT_FIELD_MAP, ...fieldMap }
    for (const fe of error.fieldErrors) {
      const name = (map[fe.field] ?? fe.field) as Path<T>
      setError(name, { type: 'server', message: fe.message })
    }
  }
  toast.error(errorMessage(error))
}
