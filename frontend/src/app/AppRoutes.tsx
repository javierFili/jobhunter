import { lazy, type ComponentType } from 'react'
import { Navigate, Route, Routes } from 'react-router'
import { GuestOnly, RequireAdmin, RequireAuth } from '@/features/auth/guards'
import { LoginPage } from '@/features/auth/LoginPage'
import { RegisterPage } from '@/features/auth/RegisterPage'
import { PlaceholderPage } from '@/features/placeholders/PlaceholderPage'
import { AppLayout } from './layout/AppLayout'
import { allNavItems } from './layout/nav'

const DashboardPage = lazy(() => import('@/features/dashboard/DashboardPage').then((m) => ({ default: m.DashboardPage })))
const ProfilePage = lazy(() => import('@/features/profile/ProfilePage').then((m) => ({ default: m.ProfilePage })))
const page = <K extends string>(load: () => Promise<Record<K, ComponentType>>, name: K) =>
  lazy(() => load().then((m) => ({ default: m[name] })))
const ResumesPage = page(() => import('@/features/resumes/ResumesPage'), 'ResumesPage')
const ResumeEditorPage = page(() => import('@/features/resumes/ResumeEditorPage'), 'ResumeEditorPage')
const ImportReviewPage = page(() => import('@/features/resumes/import/ImportReviewPage'), 'ImportReviewPage')
const JobsPage = page(() => import('@/features/jobs/JobsPage'), 'JobsPage')
const JobDetailPage = page(() => import('@/features/jobs/JobDetailPage'), 'JobDetailPage')
const SearchesPage = page(() => import('@/features/searches/SearchesPage'), 'SearchesPage')
const SearchDetailPage = page(() => import('@/features/searches/SearchDetailPage'), 'SearchDetailPage')
const RecommendedPage = page(() => import('@/features/matching/RecommendedPage'), 'RecommendedPage')
const CoverLettersPage = page(() => import('@/features/coverletters/CoverLettersPage'), 'CoverLettersPage')
const CoverLetterEditorPage = page(() => import('@/features/coverletters/CoverLetterEditorPage'), 'CoverLetterEditorPage')
const ApplicationsPage = page(() => import('@/features/applications/ApplicationsPage'), 'ApplicationsPage')
const ApplicationDetailPage = page(() => import('@/features/applications/ApplicationDetailPage'), 'ApplicationDetailPage')
const PreparePage = page(() => import('@/features/applications/PreparePage'), 'PreparePage')
const AnalyticsPage = page(() => import('@/features/analytics/AnalyticsPage'), 'AnalyticsPage')
const IntegrationsPage = page(() => import('@/features/admin/IntegrationsPage'), 'IntegrationsPage')
const SettingsPage = lazy(() => import('@/features/settings/SettingsPage').then((m) => ({ default: m.SettingsPage })))

const placeholderItems = allNavItems.filter((i) => i.phase)

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<GuestOnly />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
      </Route>
      <Route element={<RequireAuth />}>
        <Route element={<AppLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/cvs" element={<ResumesPage />} />
          <Route path="/cvs/uploads/:uploadId" element={<ImportReviewPage />} />
          <Route path="/cvs/:resumeId" element={<ResumeEditorPage />} />
          <Route path="/jobs" element={<JobsPage />} />
          <Route path="/jobs/:jobId" element={<JobDetailPage />} />
          <Route path="/recommendations" element={<RecommendedPage />} />
          <Route path="/cover-letters" element={<CoverLettersPage />} />
          <Route path="/cover-letters/:letterId" element={<CoverLetterEditorPage />} />
          <Route path="/applications" element={<ApplicationsPage />} />
          <Route path="/applications/:applicationId" element={<ApplicationDetailPage />} />
          <Route path="/applications/:applicationId/prepare" element={<PreparePage />} />
          <Route path="/analytics" element={<AnalyticsPage />} />
          <Route path="/searches" element={<SearchesPage />} />
          <Route path="/searches/:searchId" element={<SearchDetailPage />} />
          {placeholderItems.map((item) => (
            <Route key={item.to} path={item.to} element={<PlaceholderPage item={item} />} />
          ))}
          <Route element={<RequireAdmin />}>
            <Route path="/admin/integrations" element={<IntegrationsPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Route>
    </Routes>
  )
}
