import type { ReactNode } from 'react'
import { MoreHorizontal, Pencil, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { VerifiedBadge } from '@/components/StatusBadge'
import { cn } from '@/lib/utils'

interface ItemCardProps {
  title: ReactNode
  /** Plain-text name for accessible labels. */
  name: string
  subtitle?: ReactNode
  badges?: ReactNode
  meta?: ReactNode
  children?: ReactNode
  verified?: boolean
  selected?: boolean
  onSelectedChange?: (selected: boolean) => void
  onEdit?: () => void
  onDelete?: () => void
  className?: string
  /** Leading visual (e.g. a strong project-kind marker). */
  accent?: string
}

export function ItemCard({
  title,
  name,
  subtitle,
  badges,
  meta,
  children,
  verified,
  selected,
  onSelectedChange,
  onEdit,
  onDelete,
  className,
  accent,
}: ItemCardProps) {
  return (
    <article
      aria-label={name}
      className={cn(
        'relative flex gap-3 rounded-xl border bg-card p-4 text-card-foreground shadow-xs transition-colors',
        selected && 'border-primary/60 bg-primary/5',
        accent && 'border-l-4',
        accent,
        className,
      )}
    >
      {onSelectedChange && (
        <Checkbox
          className="mt-1"
          aria-label={`Seleccionar ${name}`}
          checked={!!selected}
          onCheckedChange={(c) => onSelectedChange(c === true)}
        />
      )}
      <div className="min-w-0 flex-1 space-y-2">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div className="min-w-0">
            <h3 className="font-semibold leading-tight">{title}</h3>
            {subtitle && <p className="mt-0.5 text-sm text-muted-foreground">{subtitle}</p>}
          </div>
          <div className="flex flex-wrap items-center gap-1.5">
            {badges}
            {verified !== undefined && <VerifiedBadge verified={verified} />}
          </div>
        </div>
        {meta && <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">{meta}</div>}
        {children}
      </div>
      {(onEdit || onDelete) && (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon-sm" aria-label={`Acciones para ${name}`}>
              <MoreHorizontal aria-hidden />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            {onEdit && (
              <DropdownMenuItem onSelect={onEdit}>
                <Pencil aria-hidden /> Editar
              </DropdownMenuItem>
            )}
            {onDelete && (
              <DropdownMenuItem variant="destructive" onSelect={onDelete}>
                <Trash2 aria-hidden /> Eliminar
              </DropdownMenuItem>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      )}
    </article>
  )
}
