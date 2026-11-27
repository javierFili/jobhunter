import { Suspense } from 'react'
import { Outlet, useLocation } from 'react-router'
import { LoadingState } from '@/components/LoadingState'
import { Separator } from '@/components/ui/separator'
import { SidebarInset, SidebarProvider, SidebarTrigger } from '@/components/ui/sidebar'
import { NotificationsBell } from '@/features/notifications/NotificationsBell'
import { ThemeToggle } from '@/features/settings/ThemeToggle'
import { AppSidebar } from './AppSidebar'
import { allNavItems } from './nav'
import { UserMenu } from './UserMenu'

export function AppLayout() {
  const { pathname } = useLocation()
  const current = allNavItems.find((i) => (i.to === '/' ? pathname === '/' : pathname.startsWith(i.to)))
  return (
    <SidebarProvider>
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50 focus:rounded-md focus:bg-background focus:px-3 focus:py-2 focus:shadow"
      >
        Saltar al contenido
      </a>
      <AppSidebar />
      <SidebarInset>
        <header className="sticky top-0 z-20 flex h-14 shrink-0 items-center gap-2 border-b bg-background/95 px-4 backdrop-blur supports-[backdrop-filter]:bg-background/80">
          <SidebarTrigger aria-label="Mostrar u ocultar navegación" />
          <Separator orientation="vertical" className="mx-1 h-5!" />
          <span className="truncate text-sm font-medium text-muted-foreground">{current?.label ?? 'JobHunter AI'}</span>
          <div className="ml-auto flex items-center gap-1">
            <NotificationsBell />
            <ThemeToggle />
            <UserMenu />
          </div>
        </header>
        <main id="main" tabIndex={-1} className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 outline-none md:px-8 md:py-8">
          <Suspense fallback={<LoadingState />}>
            <Outlet />
          </Suspense>
        </main>
      </SidebarInset>
    </SidebarProvider>
  )
}
