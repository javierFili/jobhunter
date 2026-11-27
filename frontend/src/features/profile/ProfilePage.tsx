import { useSearchParams } from 'react-router'
import { PageHeader } from '@/components/PageHeader'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { CertificationsTab } from './tabs/CertificationsTab'
import { EducationTab } from './tabs/EducationTab'
import { ExperiencesTab } from './tabs/ExperiencesTab'
import { LanguagesTab } from './tabs/LanguagesTab'
import { PreferencesTab } from './tabs/PreferencesTab'
import { ProfilesTab } from './tabs/ProfilesTab'
import { ProjectsTab } from './tabs/ProjectsTab'
import { SkillsTab } from './tabs/SkillsTab'
import { PROFILE_TABS, type ProfileTab } from './profileTabs'

const content: Record<ProfileTab, () => React.JSX.Element> = {
  profiles: ProfilesTab,
  experience: ExperiencesTab,
  projects: ProjectsTab,
  education: EducationTab,
  certifications: CertificationsTab,
  skills: SkillsTab,
  languages: LanguagesTab,
  preferences: PreferencesTab,
}

export function ProfilePage() {
  const [params, setParams] = useSearchParams()
  const requested = params.get('tab') as ProfileTab | null
  const tab: ProfileTab = requested && requested in content ? requested : 'profiles'

  return (
    <>
      <PageHeader
        title="Mi perfil"
        description="Tu información profesional verificada es la base para recomendaciones, CV y cartas."
      />
      <Tabs value={tab} onValueChange={(v) => setParams({ tab: v }, { replace: true })}>
        <div className="-mx-4 overflow-x-auto px-4 pb-1 md:mx-0 md:px-0">
          <TabsList className="w-max">
            {PROFILE_TABS.map((t) => (
              <TabsTrigger key={t.value} value={t.value}>
                {t.label}
              </TabsTrigger>
            ))}
          </TabsList>
        </div>
        {PROFILE_TABS.map((t) => {
          const Content = content[t.value]
          return (
            <TabsContent key={t.value} value={t.value} className="pt-4">
              <Content />
            </TabsContent>
          )
        })}
      </Tabs>
    </>
  )
}
