// RFC 9457 problem details as returned by the backend.
export interface FieldError {
  field: string
  message: string
}

export interface ProblemDetail {
  type?: string
  title?: string
  status: number
  detail?: string
  code?: string
  errors?: FieldError[]
}

export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail

  constructor(problem: ProblemDetail) {
    super(problem.detail || problem.title || defaultMessage(problem.status))
    this.name = 'ApiError'
    this.status = problem.status
    this.problem = problem
  }

  get code(): string | undefined {
    return this.problem.code
  }

  get fieldErrors(): FieldError[] {
    return this.problem.errors ?? []
  }
}

export function defaultMessage(status: number): string {
  if (status === 0) return 'No se pudo conectar con el servidor. Revisa tu conexión.'
  if (status === 401) return 'Tu sesión expiró. Inicia sesión nuevamente.'
  if (status === 403) return 'No tienes permiso para realizar esta acción.'
  if (status === 404) return 'El recurso solicitado no existe.'
  if (status === 409) return 'Otro cambio se guardó antes que el tuyo. Recarga los datos e inténtalo de nuevo.'
  if (status === 429) return 'Demasiados intentos. Espera un momento e inténtalo de nuevo.'
  if (status >= 500) return 'Ocurrió un error en el servidor. Inténtalo más tarde.'
  return 'No se pudo completar la solicitud.'
}

export async function parseProblem(response: Response): Promise<ProblemDetail> {
  let body: unknown
  try {
    const text = await response.text()
    body = text ? JSON.parse(text) : null
  } catch {
    body = null
  }
  if (body && typeof body === 'object') {
    const p = body as Partial<ProblemDetail>
    return {
      type: p.type,
      title: p.title,
      status: typeof p.status === 'number' ? p.status : response.status,
      detail: p.detail,
      code: p.code,
      errors: Array.isArray(p.errors) ? p.errors : undefined,
    }
  }
  return { status: response.status }
}

/** Human-readable message for any thrown value (used in toasts). */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'CONFLICT' && !error.problem.detail) return defaultMessage(409)
    return error.message
  }
  if (error instanceof Error && error.message) return error.message
  return defaultMessage(-1)
}
