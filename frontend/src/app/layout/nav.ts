import {
  BarChart3,
  Bookmark,
  Briefcase,
  FileText,
  LayoutDashboard,
  Mail,
  Plug,
  Search,
  Settings,
  Sparkles,
  UserRound,
  type LucideIcon,
} from 'lucide-react'

export interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  adminOnly?: boolean
  /** Phase in which the page becomes available (undefined = available now). */
  phase?: number
}

export interface NavSection {
  label: string
  items: NavItem[]
}

export const navSections: NavSection[] = [
  {
    label: 'General',
    items: [{ to: '/', label: 'Panel', icon: LayoutDashboard }],
  },
  {
    label: 'Empleos',
    items: [
      { to: '/jobs', label: 'Descubrir empleos', icon: Search },
      { to: '/searches', label: 'Búsquedas guardadas', icon: Bookmark },
      { to: '/recommendations', label: 'Empleos recomendados', icon: Sparkles },
    ],
  },
  {
    label: 'Mi carrera',
    items: [
      { to: '/profile', label: 'Mi perfil', icon: UserRound },
      { to: '/cvs', label: 'Mis CV', icon: FileText },
      { to: '/applications', label: 'Postulaciones', icon: Briefcase },
      { to: '/cover-letters', label: 'Cartas de presentación', icon: Mail },
      { to: '/analytics', label: 'Analítica', icon: BarChart3 },
    ],
  },
  {
    label: 'Cuenta',
    items: [
      { to: '/settings', label: 'Configuración', icon: Settings },
      { to: '/admin/integrations', label: 'Integraciones', icon: Plug, adminOnly: true },
    ],
  },
]

export const allNavItems: NavItem[] = navSections.flatMap((s) => s.items)
