import { useEffect, useRef } from 'react'
import { ApiError } from './problem'

export type TaskState = 'NONE' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export interface TaskStateDto {
  state: TaskState
  code: string | null
  message: string | null
}

export const TASK_POLL_MS = 2000

/** refetchInterval helper: poll every 2 s while the task is RUNNING. */
export const pollWhileRunning = (state: TaskState | undefined) => (state === 'RUNNING' ? TASK_POLL_MS : false)

export const taskErrorMessages: Record<string, string> = {
  CONSENT_REQUIRED: 'Necesitas dar tu consentimiento para que el proveedor de IA procese estos datos.',
  INTEGRATION_DISABLED: 'El proveedor de IA está desactivado. Un administrador debe activar DeepSeek o Claude en Integraciones.',
  BUDGET_EXCEEDED: 'Se alcanzó el presupuesto mensual configurado para la IA.',
  LIMIT_REACHED: 'Se alcanzó el límite de uso configurado para la IA. Inténtalo más tarde.',
  INSUFFICIENT_BALANCE: 'La cuenta del proveedor de IA no tiene saldo suficiente.',
  AI_UNAVAILABLE: 'El proveedor de IA no está disponible en este momento. Inténtalo más tarde.',
  AI_REFUSED: 'La IA se negó a procesar este contenido.',
  INVALID_OUTPUT: 'La IA devolvió una respuesta inválida. Puedes intentarlo de nuevo.',
  ALREADY_RUNNING: 'Ya hay un proceso en curso. Espera a que termine.',
  BUSY: 'El servidor está ocupado procesando otras tareas. Inténtalo en unos segundos.',
}

export function taskErrorMessage(code: string | null | undefined, fallback?: string | null): string {
  return (code && taskErrorMessages[code]) || fallback || 'La tarea no se pudo completar.'
}

/** Code from a failed POST (409 ALREADY_RUNNING, 503 BUSY, CONSENT_REQUIRED…). */
export function apiErrorCode(error: unknown): string | null {
  if (!(error instanceof ApiError)) return null
  if (error.code) return error.code
  if (error.status === 503) return 'BUSY'
  return null
}

/** Calls `onFinish` once when a task observed as RUNNING ends (SUCCEEDED or FAILED). */
export function useOnTaskFinished(state: TaskState | undefined, onFinish: (state: TaskState) => void) {
  const sawRunning = useRef(false)
  const cb = useRef(onFinish)
  useEffect(() => {
    cb.current = onFinish
  })
  useEffect(() => {
    if (state === 'RUNNING') sawRunning.current = true
    else if (state && sawRunning.current) {
      sawRunning.current = false
      cb.current(state)
    }
  }, [state])
}
