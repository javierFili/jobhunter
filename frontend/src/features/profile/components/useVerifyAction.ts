import { toast } from 'sonner'
import { errorMessage } from '@/lib/problem'
import type { VerifyRequest } from '@/lib/types'
import { useVerify } from '../api'

/** Bulk "Marcar como verificado" for one kind of item. */
export function useVerifyAction<K extends keyof VerifyRequest>(key: K, onDone: () => void) {
  const verify = useVerify()
  const run = (ids: VerifyRequest[K]) => {
    if (ids.length === 0) return
    verify.mutate({ [key]: ids } as Partial<VerifyRequest>, {
      onSuccess: () => {
        toast.success(ids.length === 1 ? 'Elemento marcado como verificado' : `${ids.length} elementos marcados como verificados`)
        onDone()
      },
      onError: (e) => toast.error(errorMessage(e)),
    })
  }
  return { run, pending: verify.isPending }
}
