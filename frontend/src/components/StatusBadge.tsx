import type { ReactNode } from 'react'
import { BadgeCheck, CircleDashed } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

export type BadgeTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger' | 'violet' | 'teal'

const toneClasses: Record<BadgeTone, string> = {
  neutral: 'bg-muted text-foreground border-border',
  info: 'bg-sky-100 text-sky-900 border-sky-200 dark:bg-sky-950 dark:text-sky-100 dark:border-sky-800',
  success: 'bg-emerald-100 text-emerald-900 border-emerald-200 dark:bg-emerald-950 dark:text-emerald-100 dark:border-emerald-800',
  warning: 'bg-amber-100 text-amber-900 border-amber-200 dark:bg-amber-950 dark:text-amber-100 dark:border-amber-800',
  danger: 'bg-red-100 text-red-900 border-red-200 dark:bg-red-950 dark:text-red-100 dark:border-red-800',
  violet: 'bg-violet-100 text-violet-900 border-violet-200 dark:bg-violet-950 dark:text-violet-100 dark:border-violet-800',
  teal: 'bg-teal-100 text-teal-900 border-teal-200 dark:bg-teal-950 dark:text-teal-100 dark:border-teal-800',
}

interface StatusBadgeProps {
  tone?: BadgeTone
  children: ReactNode
  icon?: ReactNode
  className?: string
  title?: string
}

export function StatusBadge({ tone = 'neutral', children, icon, className, title }: StatusBadgeProps) {
  return (
    <Badge variant="outline" className={cn(toneClasses[tone], className)} title={title}>
      {icon}
      {children}
    </Badge>
  )
}

export function VerifiedBadge({ verified }: { verified: boolean }) {
  return verified ? (
    <StatusBadge tone="success" icon={<BadgeCheck aria-hidden />}>
      Verificado
    </StatusBadge>
  ) : (
    <StatusBadge tone="warning" icon={<CircleDashed aria-hidden />}>
      Sin verificar
    </StatusBadge>
  )
}
