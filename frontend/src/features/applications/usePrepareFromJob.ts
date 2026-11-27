import { useState } from 'react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { api } from '@/lib/api'
import { ApiError, errorMessage } from '@/lib/problem'
import type { ApplicationDto } from './types'

/** Job detail "Preparar postulación": find the application for the job or create it, then open the prepare view. */
export function usePrepareFromJob(jobId: number) {
  const navigate = useNavigate()
  const [pending, setPending] = useState(false)
  const run = async () => {
    setPending(true)
    try {
      let app: ApplicationDto
      try {
        app = await api.get<ApplicationDto>(`/applications/by-job/${jobId}`)
      } catch (e) {
        if (!(e instanceof ApiError && e.status === 404)) throw e
        try {
          app = await api.post<ApplicationDto>('/applications', { jobOfferId: jobId })
          toast.success('Postulación creada')
        } catch (err) {
          if (err instanceof ApiError && err.code === 'APPLICATION_EXISTS') app = await api.get<ApplicationDto>(`/applications/by-job/${jobId}`)
          else throw err
        }
      }
      navigate(`/applications/${app.id}/prepare`)
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      setPending(false)
    }
  }
  return { run, pending }
}
