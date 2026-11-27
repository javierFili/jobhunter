import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { createCrudHooks } from '@/lib/crud'
import { queryKeys } from '@/lib/queryKeys'
import type {
  CatalogSkill,
  CertificationDto,
  CertificationRequest,
  EducationDto,
  EducationRequest,
  ExperienceDto,
  ExperienceRequest,
  LanguageDto,
  LanguageRequest,
  PreferencesDto,
  ProfileDto,
  ProfileItemsRequest,
  ProfileRequest,
  ProjectDto,
  ProjectRequest,
  SkillCategory,
  UserSkillDto,
  UserSkillRequest,
  VerifyRequest,
} from '@/lib/types'
import { ApiError } from '@/lib/problem'

export const profilesApi = createCrudHooks<ProfileDto, ProfileRequest>('/profiles', queryKeys.profiles)
export const experiencesApi = createCrudHooks<ExperienceDto, ExperienceRequest>('/experiences', queryKeys.experiences)
export const projectsApi = createCrudHooks<ProjectDto, ProjectRequest>('/projects', queryKeys.projects)
export const educationsApi = createCrudHooks<EducationDto, EducationRequest>('/educations', queryKeys.educations)
export const certificationsApi = createCrudHooks<CertificationDto, CertificationRequest>(
  '/certifications',
  queryKeys.certifications,
)

export function useUpdateProfileItems() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: ProfileItemsRequest }) =>
      api.put<ProfileDto>(`/profiles/${id}/items`, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.profiles })
    },
  })
}

export const emptyVerifyRequest = (): VerifyRequest => ({
  experienceIds: [],
  projectIds: [],
  educationIds: [],
  certificationIds: [],
  skillIds: [],
  languageCodes: [],
})

export function useVerify() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (partial: Partial<VerifyRequest>) =>
      api.post<void>('/profile/verify', { ...emptyVerifyRequest(), ...partial }),
    onSuccess: () => {
      for (const key of [
        queryKeys.experiences,
        queryKeys.projects,
        queryKeys.educations,
        queryKeys.certifications,
        queryKeys.skills,
        queryKeys.languages,
        queryKeys.dashboard,
      ]) {
        void qc.invalidateQueries({ queryKey: key })
      }
    },
  })
}

// ---- Skills ----

export function useCatalogSkills(q: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.catalogSkills(q),
    queryFn: ({ signal }) => api.get<CatalogSkill[]>('/catalog/skills', { q, limit: 20 }, signal),
    enabled,
    placeholderData: keepPreviousData,
    staleTime: 60_000,
  })
}

export function useCreateCatalogSkill() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { name: string; category: SkillCategory }) => api.post<CatalogSkill>('/catalog/skills', body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['catalog', 'skills'] })
    },
  })
}

export function useUserSkills() {
  return useQuery({ queryKey: queryKeys.skills, queryFn: ({ signal }) => api.get<UserSkillDto[]>('/skills', undefined, signal) })
}

function useInvalidateSkills() {
  const qc = useQueryClient()
  return () => {
    void qc.invalidateQueries({ queryKey: queryKeys.skills })
    void qc.invalidateQueries({ queryKey: queryKeys.dashboard })
  }
}

export function useUpsertUserSkill() {
  const invalidate = useInvalidateSkills()
  return useMutation({
    mutationFn: ({ skillId, body }: { skillId: number; body: UserSkillRequest }) =>
      api.put<UserSkillDto>(`/skills/${skillId}`, body),
    onSuccess: invalidate,
  })
}

export function useDeleteUserSkill() {
  const invalidate = useInvalidateSkills()
  return useMutation({ mutationFn: (skillId: number) => api.delete(`/skills/${skillId}`), onSuccess: invalidate })
}

// ---- Languages ----

export function useLanguages() {
  return useQuery({ queryKey: queryKeys.languages, queryFn: ({ signal }) => api.get<LanguageDto[]>('/languages', undefined, signal) })
}

export function useReplaceLanguages() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: LanguageRequest[]) => api.put<LanguageDto[]>('/languages', body),
    onSuccess: (list) => {
      qc.setQueryData(queryKeys.languages, list)
      void qc.invalidateQueries({ queryKey: queryKeys.dashboard })
    },
  })
}

// ---- Preferences ----

export const emptyPreferences = (): PreferencesDto => ({
  regions: [],
  countriesInclude: [],
  countriesExclude: [],
  modalities: [],
  contractTypes: [],
  engagementTypes: [],
  minSalary: null,
  salaryCurrency: null,
  salaryPeriod: null,
  workLanguages: [],
  seniority: null,
  relocationOk: false,
})

export function usePreferences() {
  return useQuery({
    queryKey: queryKeys.preferences,
    queryFn: async ({ signal }) => {
      try {
        return { ...emptyPreferences(), ...(await api.get<PreferencesDto>('/preferences', undefined, signal)) }
      } catch (e) {
        // Not configured yet → start from empty preferences.
        if (e instanceof ApiError && e.status === 404) return emptyPreferences()
        throw e
      }
    },
  })
}

export function useSavePreferences() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: PreferencesDto) => api.put<PreferencesDto>('/preferences', body),
    onSuccess: (prefs) => {
      qc.setQueryData(queryKeys.preferences, { ...emptyPreferences(), ...prefs })
      void qc.invalidateQueries({ queryKey: queryKeys.dashboard })
    },
  })
}
