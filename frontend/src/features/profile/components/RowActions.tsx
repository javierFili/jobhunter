import { Pencil, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'

export function RowActions({ name, onEdit, onDelete }: { name: string; onEdit?: () => void; onDelete?: () => void }) {
  return (
    <div className="flex justify-end gap-1">
      {onEdit && (
        <Button variant="ghost" size="icon-sm" onClick={onEdit} aria-label={`Editar ${name}`}>
          <Pencil aria-hidden />
        </Button>
      )}
      {onDelete && (
        <Button variant="ghost" size="icon-sm" onClick={onDelete} aria-label={`Eliminar ${name}`} className="text-destructive">
          <Trash2 aria-hidden />
        </Button>
      )}
    </div>
  )
}
