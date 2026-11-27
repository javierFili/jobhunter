import { AlertTriangle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { errorMessage } from '@/lib/problem'
import { EmptyState } from './EmptyState'

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <EmptyState
      icon={AlertTriangle}
      title="No se pudieron cargar los datos"
      description={errorMessage(error)}
      action={
        onRetry && (
          <Button variant="outline" onClick={onRetry}>
            Reintentar
          </Button>
        )
      }
    />
  )
}
