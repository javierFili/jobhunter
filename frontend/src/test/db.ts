// In-memory backend state for MSW handlers (TEST DATA only).
import type { ConsentDto, ExperienceDto, ProfileDto, ProjectDto, UserDto, UserSkillDto } from '@/lib/types'
import * as f from './fixtures'

const clone = <T>(v: T): T => structuredClone(v)

export const db = {
  user: clone(f.testUser) as UserDto,
  experiences: clone(f.experiences) as ExperienceDto[],
  projects: clone(f.projects) as ProjectDto[],
  profiles: [] as ProfileDto[],
  skills: clone(f.userSkills) as UserSkillDto[],
  consents: clone(f.consents) as ConsentDto[],
  nextId: 1000,
}

export function resetDb(): void {
  db.user = clone(f.testUser)
  db.experiences = clone(f.experiences)
  db.projects = clone(f.projects)
  db.profiles = []
  db.skills = clone(f.userSkills)
  db.consents = clone(f.consents)
  db.nextId = 1000
}
