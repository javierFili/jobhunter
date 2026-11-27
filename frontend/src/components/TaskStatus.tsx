import { Link } from 'react-router'
import { AlertTriangle, Loader2 } from 'lucide-react'
import { useAuth } from '@/features/auth/auth-context'
import { taskErrorMessage, type TaskStateDto } from '@/lib/task'

/** Running indicator or a Spanish explanation of a failed AI task, with the relevant link. */
export function TaskStatus({ task, runningText = 'Procesando con IA…' }: { task: TaskStateDto | null | undefined; runningText?: string }) {
  const { user } = useAuth()
  if (!task || task.state === 'NONE' || task.state === 'SUCCEEDED') return null
  if (task.state === 'RUNNING') {
    return (
      <p role="status" className="flex items-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="size-4 animate-spin" aria-hidden /> {runningText}
      </p>
    )
  }
  return <TaskError code={task.code} message={task.message} isAdmin={user?.role === 'ADMIN'} />
}

export function TaskError({ code, message, isAdmin }: { code: string | null; message?: string | null; isAdmin?: boolean }) {
  return (
    <div role="alert" className="flex items-start gap-2 rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
      <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
      <div className="grid gap-1">
        <p>{taskErrorMessage(code, message)}</p>
        {code === 'CONSENT_REQUIRED' && (
          <Link to="/settings#consentimientos" className="font-medium underline underline-offset-2">
            Ir a Configuración → Consentimientos de IA
          </Link>
        )}
        {code === 'INTEGRATION_DISABLED' && isAdmin && (
          <Link to="/admin/integrations" className="font-medium underline underline-offset-2">
            Ir a Integraciones
          </Link>
        )}
      </div>
    </div>
  )
}
