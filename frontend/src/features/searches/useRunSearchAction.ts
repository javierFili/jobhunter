import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { ApiError, errorMessage } from '@/lib/problem'
import { useRunSearch } from './api'

export function useRunSearchAction() {
  const run = useRunSearch()
  const navigate = useNavigate()
  const start = (searchId: number) =>
    run.mutate(searchId, {
      onSuccess: (r) => {
        toast.success('Búsqueda en ejecución')
        navigate(`/searches/${searchId}?execution=${r.executionId}`)
      },
      onError: (e) =>
        toast.error(e instanceof ApiError && e.code === 'ALREADY_RUNNING' ? 'Esta búsqueda ya se está ejecutando. Espera a que termine.' : errorMessage(e)),
    })
  return { start, pending: run.isPending, runningId: run.isPending ? run.variables : undefined }
}
