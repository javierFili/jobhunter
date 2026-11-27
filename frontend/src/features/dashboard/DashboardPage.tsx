import { Link } from 'react-router'
import {
  ArrowRight,
  Award,
  Briefcase,
  CheckCircle2,
  FolderGit2,
  GraduationCap,
  Languages,
  Search,
  UserSquare2,
  Wrench,
  type LucideIcon,
} from 'lucide-react'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/features/auth/auth-context'
import { UsageCard } from '@/features/admin/UsageCard'
import { useOverview, useTimeline } from '@/features/analytics/api'
import { TimelineChart } from '@/features/analytics/charts'
import { lastDays, percent } from '@/features/analytics/labels'
import { useUserSkills } from '@/features/profile/api'
import { formatRelative } from '@/lib/format'
import type { DashboardSummary, MissingSection } from '@/lib/types'
import { useDashboardSummary } from './api'
import { SkillsEvidenceChart } from './SkillsEvidenceChart'

const missingInfo: Record<MissingSection, { label: string; tab: string }> = {
  headline: { label: 'Agrega un titular a tu perfil', tab: 'profiles' },
  summary: { label: 'Escribe un resumen profesional', tab: 'profiles' },
  experience: { label: 'Registra tu experiencia laboral', tab: 'experience' },
  skills: { label: 'Agrega tus habilidades', tab: 'skills' },
  languages: { label: 'Indica los idiomas que hablas', tab: 'languages' },
  preferences: { label: 'Configura tus preferencias laborales', tab: 'preferences' },
  education: { label: 'Agrega tu formación', tab: 'education' },
}

const counters: { key: keyof DashboardSummary['counts']; label: string; icon: LucideIcon; tab: string }[] = [
  { key: 'profiles', label: 'Perfiles', icon: UserSquare2, tab: 'profiles' },
  { key: 'experiences', label: 'Experiencias', icon: Briefcase, tab: 'experience' },
  { key: 'projects', label: 'Proyectos', icon: FolderGit2, tab: 'projects' },
  { key: 'educations', label: 'Formación', icon: GraduationCap, tab: 'education' },
  { key: 'certifications', label: 'Certificaciones', icon: Award, tab: 'certifications' },
  { key: 'skills', label: 'Habilidades', icon: Wrench, tab: 'skills' },
  { key: 'languages', label: 'Idiomas', icon: Languages, tab: 'languages' },
]

function CompletenessCard({ summary }: { summary: DashboardSummary }) {
  const pct = Math.max(0, Math.min(100, Math.round(summary.profileCompleteness)))
  return (
    <Card className="lg:col-span-2">
      <CardHeader>
        <CardTitle>
          <h2 className="text-base font-semibold">Completitud del perfil</h2>
        </CardTitle>
        <CardDescription>Un perfil completo mejora la calidad de las recomendaciones.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        <div className="flex items-end gap-3">
          <span className="text-4xl font-semibold tabular-nums">{pct}%</span>
        </div>
        <Progress value={pct} aria-label="Completitud del perfil" aria-valuetext={`${pct}%`} />
        {summary.missingSections.length === 0 ? (
          <p className="flex items-center gap-2 text-sm text-emerald-700 dark:text-emerald-400">
            <CheckCircle2 className="size-4" aria-hidden /> ¡Tu perfil está completo!
          </p>
        ) : (
          <div>
            <h3 className="mb-2 text-sm font-medium">Pendiente</h3>
            <ul className="grid gap-1 sm:grid-cols-2">
              {summary.missingSections.map((s) => (
                <li key={s}>
                  <Link
                    to={`/profile?tab=${missingInfo[s].tab}`}
                    className="group flex items-center justify-between gap-2 rounded-lg border px-3 py-2 text-sm hover:bg-muted"
                  >
                    {missingInfo[s].label}
                    <ArrowRight className="size-4 text-muted-foreground transition-transform group-hover:translate-x-0.5" aria-hidden />
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function SkillsCard() {
  const { data } = useUserSkills()
  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h2 className="text-base font-semibold">Habilidades por nivel de evidencia</h2>
        </CardTitle>
        <CardDescription>Según dónde usaste cada habilidad.</CardDescription>
      </CardHeader>
      <CardContent>
        {!data ? (
          <Skeleton className="h-56 w-full" />
        ) : data.length === 0 ? (
          <EmptyState
            icon={Wrench}
            title="Sin habilidades todavía"
            description="El gráfico aparecerá cuando registres tus habilidades."
            action={
              <Link to="/profile?tab=skills" className="text-sm font-medium text-primary hover:underline">
                Agregar habilidades
              </Link>
            }
          />
        ) : (
          <SkillsEvidenceChart skills={data} />
        )}
      </CardContent>
    </Card>
  )
}

function ApplicationsCard() {
  const period = lastDays(30)
  const overview = useOverview(period)
  const timeline = useTimeline(period)
  const apps = overview.data?.applications
  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h2 className="text-base font-semibold">Postulaciones</h2>
        </CardTitle>
        <CardDescription>Últimos 30 días</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        {!apps ? (
          <Skeleton className="h-16 w-full" />
        ) : (
          <ul className="grid grid-cols-3 gap-3" aria-label="Resumen de postulaciones">
            {(
              [
                ['Enviadas', apps.applied],
                ['Entrevistas', apps.interviews],
                ['Tasa de respuesta', percent(apps.responseRate)],
              ] as const
            ).map(([label, value]) => (
              <li key={label}>
                <Link to="/applications" className="flex h-full flex-col gap-1 rounded-lg border p-3 hover:bg-muted/50">
                  <span className="text-2xl font-semibold tabular-nums">{value}</span>
                  <span className="text-xs text-muted-foreground">{label}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
        {timeline.data && timeline.data.length > 0 && (
          <div>
            <h3 className="mb-1 text-sm font-medium">Ofertas nuevas por día</h3>
            <TimelineChart data={timeline.data} height={140} />
          </div>
        )}
        <Link to="/analytics" className="text-sm font-medium text-primary hover:underline">
          Ver analítica completa
        </Link>
      </CardContent>
    </Card>
  )
}

function JobsCard({ summary }: { summary: DashboardSummary | undefined }) {
  const jobs = summary?.jobs
  const searches = summary?.searches
  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h2 className="text-base font-semibold">Empleos</h2>
        </CardTitle>
        {searches && (
          <CardDescription>
            {searches.count} {searches.count === 1 ? 'búsqueda guardada' : 'búsquedas guardadas'}
            {searches.lastRunAt ? ` · última ejecución ${formatRelative(searches.lastRunAt)}` : ' · aún sin ejecutar'}
          </CardDescription>
        )}
      </CardHeader>
      <CardContent>
        {!jobs ? (
          <EmptyState
            icon={Search}
            title="Aún no hay ofertas"
            description="Crea una búsqueda guardada para traer ofertas de las fuentes."
            action={
              <Link to="/searches" className="text-sm font-medium text-primary hover:underline">
                Crear búsqueda
              </Link>
            }
          />
        ) : (
          <ul className="grid grid-cols-2 gap-3" aria-label="Resumen de ofertas">
            {(
              [
                ['Ofertas en total', jobs.total, '/jobs'],
                ['Nuevas (7 días)', jobs.newLast7Days, '/jobs?postedWithinDays=7'],
                ['Accesibles o probables desde Bolivia', jobs.eligibleOrLikely, '/jobs?eligibility=ELIGIBLE,LIKELY'],
                ['Guardadas', jobs.saved, '/jobs?saved=true'],
              ] as const
            ).map(([label, value, to]) => (
              <li key={label}>
                <Link to={to} className="flex h-full flex-col gap-1 rounded-lg border p-3 hover:bg-muted/50">
                  <span className="text-2xl font-semibold tabular-nums">{value}</span>
                  <span className="text-xs text-muted-foreground">{label}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}

export function DashboardPage() {
  const { user } = useAuth()
  const { data, isLoading, error, refetch } = useDashboardSummary()
  const firstName = user?.fullName.split(' ')[0]

  return (
    <>
      <PageHeader title={`Hola${firstName ? `, ${firstName}` : ''}`} description="Este es el estado de tu búsqueda de empleo." />
      {error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <div className="grid gap-4">
          <div className="grid gap-4 lg:grid-cols-3">
            {isLoading || !data ? <Skeleton className="h-64 rounded-xl lg:col-span-2" /> : <CompletenessCard summary={data} />}
            <SkillsCard />
          </div>

          <section aria-labelledby="counters-title">
            <h2 id="counters-title" className="sr-only">
              Resumen de tu perfil
            </h2>
            <ul className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-7">
              {counters.map((c) => (
                <li key={c.key}>
                  <Link
                    to={`/profile?tab=${c.tab}`}
                    className="flex h-full flex-col gap-2 rounded-xl border bg-card p-4 transition-colors hover:bg-muted/50"
                  >
                    <c.icon className="size-4 text-muted-foreground" aria-hidden />
                    {isLoading || !data ? (
                      <Skeleton className="h-7 w-10" />
                    ) : (
                      <span className="text-2xl font-semibold tabular-nums">{data.counts[c.key]}</span>
                    )}
                    <span className="text-xs text-muted-foreground">{c.label}</span>
                  </Link>
                </li>
              ))}
            </ul>
          </section>

          {user?.role === 'ADMIN' && <UsageCard compact />}
          <div className="grid gap-4 md:grid-cols-2">
            <JobsCard summary={data} />
            <ApplicationsCard />
          </div>
        </div>
      )}
    </>
  )
}
