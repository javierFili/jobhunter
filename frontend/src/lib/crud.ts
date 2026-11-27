import { useMutation, useQuery, useQueryClient, type QueryKey } from '@tanstack/react-query'
import { api } from './api'
import { queryKeys } from './queryKeys'

/** Generic list/create/update/delete hooks for a REST collection with optimistic locking. */
export function createCrudHooks<TDto extends { id: number }, TRequest>(path: string, key: QueryKey) {
  function useInvalidate() {
    const qc = useQueryClient()
    return () => {
      void qc.invalidateQueries({ queryKey: key })
      void qc.invalidateQueries({ queryKey: queryKeys.dashboard })
      void qc.invalidateQueries({ queryKey: queryKeys.profiles })
    }
  }

  function useList() {
    return useQuery({ queryKey: key, queryFn: ({ signal }) => api.get<TDto[]>(path, undefined, signal) })
  }

  function useCreate() {
    const invalidate = useInvalidate()
    return useMutation({
      mutationFn: (body: TRequest) => api.post<TDto>(path, body),
      onSuccess: invalidate,
    })
  }

  function useUpdate() {
    const invalidate = useInvalidate()
    return useMutation({
      mutationFn: ({ id, body }: { id: number; body: TRequest }) => api.put<TDto>(`${path}/${id}`, body),
      onSuccess: invalidate,
      // On a stale version (409 CONFLICT) refresh the list so the user sees the latest data.
      onError: invalidate,
    })
  }

  function useDelete() {
    const invalidate = useInvalidate()
    return useMutation({
      mutationFn: (id: number) => api.delete(`${path}/${id}`),
      onSuccess: invalidate,
    })
  }

  return { useList, useCreate, useUpdate, useDelete }
}
