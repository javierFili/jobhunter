import { NavLink, useLocation } from 'react-router'
import { Logo } from '@/components/Logo'
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuBadge,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
  useSidebar,
} from '@/components/ui/sidebar'
import { useAuth } from '@/features/auth/auth-context'
import { navSections } from './nav'

export function AppSidebar() {
  const { user } = useAuth()
  const { pathname } = useLocation()
  const { isMobile, setOpenMobile } = useSidebar()
  const isAdmin = user?.role === 'ADMIN'

  const isActive = (to: string) => (to === '/' ? pathname === '/' : pathname === to || pathname.startsWith(`${to}/`))

  return (
    <Sidebar collapsible="icon" aria-label="Navegación principal">
      <SidebarHeader className="h-14 justify-center border-b">
        <NavLink to="/" className="rounded-md px-1 outline-none focus-visible:ring-2 focus-visible:ring-ring" aria-label="JobHunter AI, ir al panel">
          <Logo className="group-data-[collapsible=icon]:[&>span:last-child]:hidden" />
        </NavLink>
      </SidebarHeader>
      <SidebarContent>
        {navSections.map((section) => {
          const items = section.items.filter((i) => !i.adminOnly || isAdmin)
          if (items.length === 0) return null
          return (
            <SidebarGroup key={section.label}>
              <SidebarGroupLabel>{section.label}</SidebarGroupLabel>
              <SidebarGroupContent>
                <SidebarMenu>
                  {items.map((item) => (
                    <SidebarMenuItem key={item.to}>
                      <SidebarMenuButton asChild isActive={isActive(item.to)} tooltip={item.label}>
                        <NavLink
                          to={item.to}
                          end={item.to === '/'}
                          onClick={() => isMobile && setOpenMobile(false)}
                        >
                          <item.icon aria-hidden />
                          <span>{item.label}</span>
                        </NavLink>
                      </SidebarMenuButton>
                      {item.phase && (
                        <SidebarMenuBadge className="text-[10px] font-normal text-muted-foreground" aria-hidden title={`Disponible en la Fase ${item.phase}`}>
                          F{item.phase}
                        </SidebarMenuBadge>
                      )}
                    </SidebarMenuItem>
                  ))}
                </SidebarMenu>
              </SidebarGroupContent>
            </SidebarGroup>
          )
        })}
      </SidebarContent>
      <SidebarRail />
    </Sidebar>
  )
}
