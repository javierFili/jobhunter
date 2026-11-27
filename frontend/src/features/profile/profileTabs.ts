export type ProfileTab =
  | 'profiles'
  | 'experience'
  | 'projects'
  | 'education'
  | 'certifications'
  | 'skills'
  | 'languages'
  | 'preferences'

export const PROFILE_TABS: { value: ProfileTab; label: string }[] = [
  { value: 'profiles', label: 'Perfiles profesionales' },
  { value: 'experience', label: 'Experiencia laboral' },
  { value: 'projects', label: 'Proyectos' },
  { value: 'education', label: 'Educación' },
  { value: 'certifications', label: 'Certificaciones' },
  { value: 'skills', label: 'Habilidades' },
  { value: 'languages', label: 'Idiomas' },
  { value: 'preferences', label: 'Preferencias laborales' },
]
