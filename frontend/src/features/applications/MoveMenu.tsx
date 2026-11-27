import { ArrowRightLeft, Send } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { ApiError, errorMessage } from '@/lib/problem'
import { useTransition, useTransitions } from './api'
import { PRE_APPLIED, statusLabels } from './labels'
import type { ApplicationDto } from './types'

/** Keyboard/screen-reader friendly alternative to drag & drop. */
export function MoveMenu({ application, onMarkSubmitted, size = 'icon-sm' }: { application: ApplicationDto; onMarkSubmitted: () => void; size?: 'icon-sm' | 'sm' }) {
  const transitions = useTransitions()
  const transition = useTransition()
  const targets = (transitions.data?.[application.status] ?? []).filter((s) => s !== 'APPLIED')
  const canSubmit = PRE_APPLIED.includes(application.status)
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant={size === 'sm' ? 'outline' : 'ghost'} size={size} aria-label={`Cambiar estado de ${application.positionTitle} en ${application.companyName}`}>
          <ArrowRightLeft aria-hidden />
          {size === 'sm' && 'Cambiar estado'}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuLabel>Mover a…</DropdownMenuLabel>
        {targets.length === 0 && !canSubmit && <DropdownMenuItem disabled>Sin transiciones disponibles</DropdownMenuItem>}
        {targets.map((s) => (
          <DropdownMenuItem
            key={s}
            onSelect={() =>
              transition.mutate(
                { id: application.id, toStatus: s },
                {
                  onSuccess: () => toast.success(`Movida a «${statusLabels[s]}»`),
                  onError: (e) => toast.error(e instanceof ApiError && e.code === 'INVALID_TRANSITION' ? 'Ese cambio de estado no está permitido.' : errorMessage(e)),
                },
              )
            }
          >
            {statusLabels[s]}
          </DropdownMenuItem>
        ))}
        {canSubmit && (
          <>
            <DropdownMenuSeparator />
            <DropdownMenuItem onSelect={onMarkSubmitted}>
              <Send aria-hidden /> Marcar como enviada
            </DropdownMenuItem>
          </>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
