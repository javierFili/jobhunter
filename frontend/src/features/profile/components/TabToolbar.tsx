import type { ReactNode } from 'react'
import { BadgeCheck, Loader2, Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface TabToolbarProps {
  description: ReactNode
  addLabel?: string
  onAdd?: () => void
  selectedCount?: number
  onVerify?: () => void
  verifying?: boolean
  extra?: ReactNode
}

export function TabToolbar({ description, addLabel, onAdd, selectedCount = 0, onVerify, verifying, extra }: TabToolbarProps) {
  return (
    <div className="mb-4 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
      <p className="max-w-2xl text-sm text-muted-foreground">{description}</p>
      <div className="flex flex-wrap items-center gap-2">
        {extra}
        {onVerify && (
          <Button variant="outline" onClick={onVerify} disabled={selectedCount === 0 || verifying}>
            {verifying ? <Loader2 className="animate-spin" aria-hidden /> : <BadgeCheck aria-hidden />}
            Marcar como verificado{selectedCount > 0 ? ` (${selectedCount})` : ''}
          </Button>
        )}
        {onAdd && (
          <Button onClick={onAdd}>
            <Plus aria-hidden />
            {addLabel}
          </Button>
        )}
      </div>
    </div>
  )
}
