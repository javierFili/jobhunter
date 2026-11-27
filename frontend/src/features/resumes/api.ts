import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { pollWhileRunning, type TaskStateDto } from '@/lib/task'
import type {
  CreateResumeRequest,
  CreateResumeResponse,
  DiffLine,
  ImportRequest,
  ImportResult,
  ResumeDocument,
  ResumeDto,
  ResumeLanguage,
  ResumeTemplate,
  TemplateId,
  UploadDto,
  UploadSummaryDto,
  VersionDetailDto,
  VersionSummaryDto,
} from './types'

export const resumeKeys = {
  uploads: ['resumes', 'uploads'] as const,
  upload: (id: number) => ['resumes', 'uploads', id] as const,
  templates: ['resume-templates'] as const,
  list: ['resumes', 'list'] as const,
  resume: (id: number) => ['resumes', id] as const,
  versions: (id: number) => ['resumes', id, 'versions'] as const,
  version: (id: number, v: number) => ['resumes', id, 'versions', v] as const,
  pdf: (id: number, v: number, template: TemplateId) => ['resumes', id, 'versions', v, 'pdf', template] as const,
  compare: (id: number, from: number, to: number) => ['resumes', id, 'compare', from, to] as const,
}

// ---- uploads ----
export function useUploads() {
  return useQuery({ queryKey: resumeKeys.uploads, queryFn: ({ signal }) => api.get<UploadSummaryDto[]>('/resumes/uploads', undefined, signal) })
}

export function useUpload(id: number) {
  return useQuery({ queryKey: resumeKeys.upload(id), queryFn: ({ signal }) => api.get<UploadDto>(`/resumes/uploads/${id}`, undefined, signal) })
}

export function useUploadResume() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => {
      const form = new FormData()
      form.append('file', file)
      return api.upload<UploadDto>('/resumes/uploads', form)
    },
    onSuccess: (upload) => {
      qc.setQueryData(resumeKeys.upload(upload.id), upload)
      void qc.invalidateQueries({ queryKey: resumeKeys.uploads })
    },
  })
}

export function useDeleteUpload() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api.delete(`/resumes/uploads/${id}`),
    onSuccess: () => void qc.invalidateQueries({ queryKey: resumeKeys.uploads }),
  })
}

export function useImportUpload(id: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: ImportRequest) => api.post<ImportResult>(`/resumes/uploads/${id}/import`, body),
    onSuccess: () => {
      // Imported items land in Mi perfil.
      for (const key of [['experiences'], ['projects'], ['educations'], ['certifications'], ['skills'], ['languages'], ['dashboard'], ['resumes', 'uploads']]) {
        void qc.invalidateQueries({ queryKey: key })
      }
    },
  })
}

// ---- resumes ----
export function useResumeTemplates() {
  return useQuery({
    queryKey: resumeKeys.templates,
    queryFn: ({ signal }) => api.get<ResumeTemplate[]>('/resume-templates', undefined, signal),
    staleTime: Infinity,
  })
}

export function useResumes(enabled = true) {
  return useQuery({ queryKey: resumeKeys.list, queryFn: ({ signal }) => api.get<ResumeDto[]>('/resumes', undefined, signal), enabled })
}

export function useResume(id: number) {
  return useQuery({ queryKey: resumeKeys.resume(id), queryFn: ({ signal }) => api.get<ResumeDto>(`/resumes/${id}`, undefined, signal) })
}

export function useCreateResume() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateResumeRequest) => api.post<CreateResumeResponse>('/resumes', body),
    onSuccess: () => void qc.invalidateQueries({ queryKey: resumeKeys.list }),
  })
}

export function useUpdateResume(id: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { title?: string; templateId?: TemplateId; version: number }) => api.patch<ResumeDto>(`/resumes/${id}`, body),
    onSuccess: (resume) => {
      qc.setQueryData(resumeKeys.resume(id), resume)
      void qc.invalidateQueries({ queryKey: resumeKeys.list })
    },
    onError: () => void qc.invalidateQueries({ queryKey: resumeKeys.resume(id) }),
  })
}

export function useDeleteResume() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api.delete(`/resumes/${id}`),
    onSuccess: () => void qc.invalidateQueries({ queryKey: resumeKeys.list }),
  })
}

// ---- versions ----
export function useVersions(id: number, enabled = true) {
  return useQuery({
    queryKey: resumeKeys.versions(id),
    queryFn: ({ signal }) => api.get<VersionSummaryDto[]>(`/resumes/${id}/versions`, undefined, signal),
    enabled,
  })
}

export function useVersion(id: number, versionNo: number | null) {
  return useQuery({
    queryKey: resumeKeys.version(id, versionNo ?? -1),
    queryFn: ({ signal }) => api.get<VersionDetailDto>(`/resumes/${id}/versions/${versionNo}`, undefined, signal),
    enabled: versionNo != null,
  })
}

function useInvalidateVersions(id: number) {
  const qc = useQueryClient()
  return () => {
    void qc.invalidateQueries({ queryKey: resumeKeys.versions(id) })
    void qc.invalidateQueries({ queryKey: resumeKeys.resume(id) })
    void qc.invalidateQueries({ queryKey: resumeKeys.list })
  }
}

export function useSaveVersion(id: number) {
  const invalidate = useInvalidateVersions(id)
  return useMutation({
    mutationFn: (body: { content: ResumeDocument; baseVersionNo: number; note?: string }) =>
      api.post<VersionSummaryDto>(`/resumes/${id}/versions`, body),
    onSuccess: invalidate,
  })
}

export function useApproveVersion(id: number) {
  const qc = useQueryClient()
  const invalidate = useInvalidateVersions(id)
  return useMutation({
    mutationFn: (versionNo: number) => api.post<VersionSummaryDto>(`/resumes/${id}/versions/${versionNo}/approve`),
    onSuccess: (summary) => {
      invalidate()
      void qc.invalidateQueries({ queryKey: resumeKeys.version(id, summary.versionNo) })
    },
  })
}

export function useResumePdf(id: number, versionNo: number | null, template: TemplateId) {
  return useQuery({
    queryKey: resumeKeys.pdf(id, versionNo ?? -1, template),
    queryFn: ({ signal }) => api.blob(`/resumes/${id}/versions/${versionNo}/pdf`, { template, inline: true }, signal),
    enabled: versionNo != null,
    staleTime: 5 * 60_000,
  })
}

export function useCompare(id: number, from: number | null, to: number | null) {
  return useQuery({
    queryKey: resumeKeys.compare(id, from ?? -1, to ?? -1),
    queryFn: ({ signal }) => api.get<DiffLine[]>(`/resumes/${id}/compare`, { from: from!, to: to! }, signal),
    enabled: from != null && to != null && from !== to,
    placeholderData: keepPreviousData,
  })
}

// ---- AI parse (Phase 4) ----
export function useAiParseStatus(uploadId: number) {
  return useQuery({
    queryKey: ['resumes', 'uploads', uploadId, 'ai-parse'],
    queryFn: ({ signal }) => api.get<TaskStateDto>(`/resumes/uploads/${uploadId}/ai-parse`, undefined, signal),
    refetchInterval: (q) => pollWhileRunning(q.state.data?.state),
  })
}

export function useStartAiParse(uploadId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api.post<TaskStateDto>(`/resumes/uploads/${uploadId}/ai-parse`),
    onSuccess: (task) => {
      qc.setQueryData<TaskStateDto>(['resumes', 'uploads', uploadId, 'ai-parse'], { state: task.state, code: task.code ?? null, message: task.message ?? null })
      void qc.invalidateQueries({ queryKey: ['resumes', 'uploads', uploadId, 'ai-parse'] })
    },
  })
}

// ---- Phase 5: optimize a resume for a job ----
export function useOptimizeStatus(jobId: number, resumeId: number | null) {
  return useQuery({
    queryKey: ['jobs', 'detail', jobId, 'optimize', resumeId ?? -1],
    queryFn: ({ signal }) =>
      api.get<{ task: TaskStateDto; version: VersionSummaryDto | null }>(`/jobs/${jobId}/optimize-resume`, { resumeId: resumeId! }, signal),
    enabled: resumeId != null,
    refetchInterval: (q) => pollWhileRunning(q.state.data?.task.state),
  })
}

export function useOptimizeResume(jobId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { resumeId: number; language: ResumeLanguage; profileId?: number | null }) =>
      api.post<TaskStateDto>(`/jobs/${jobId}/optimize-resume`, body),
    onSuccess: (task, body) => {
      qc.setQueryData(['jobs', 'detail', jobId, 'optimize', body.resumeId], { task, version: null })
      void qc.invalidateQueries({ queryKey: ['jobs', 'detail', jobId, 'optimize', body.resumeId] })
    },
  })
}
