import { Code2, GraduationCap, Handshake, User, type LucideIcon } from 'lucide-react'
import type { BadgeTone } from '@/components/StatusBadge'
import type { ProjectKind } from '@/lib/types'

export const projectKindStyle: Record<ProjectKind, { tone: BadgeTone; icon: LucideIcon; border: string }> = {
  PERSONAL: { tone: 'violet', icon: User, border: 'border-l-violet-500' },
  ACADEMIC: { tone: 'info', icon: GraduationCap, border: 'border-l-sky-500' },
  FREELANCE_CLIENT: { tone: 'warning', icon: Handshake, border: 'border-l-amber-500' },
  OPEN_SOURCE: { tone: 'teal', icon: Code2, border: 'border-l-teal-500' },
}
