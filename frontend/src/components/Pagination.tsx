import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface PaginationProps {
  page: number
  totalPages: number
  totalElements?: number
  onPageChange: (page: number) => void
  label?: string
}

/** Zero-based pagination matching the backend Page shape. */
export function Pagination({ page, totalPages, totalElements, onPageChange, label = 'Paginación' }: PaginationProps) {
  if (totalPages <= 1) return null
  return (
    <nav aria-label={label} className="flex items-center justify-between gap-3 pt-2">
      <p className="text-sm text-muted-foreground">
        Página {page + 1} de {totalPages}
        {totalElements != null && ` · ${totalElements} resultados`}
      </p>
      <div className="flex gap-2">
        <Button variant="outline" size="sm" onClick={() => onPageChange(page - 1)} disabled={page <= 0}>
          <ChevronLeft aria-hidden /> Anterior
        </Button>
        <Button variant="outline" size="sm" onClick={() => onPageChange(page + 1)} disabled={page >= totalPages - 1}>
          Siguiente <ChevronRight aria-hidden />
        </Button>
      </div>
    </nav>
  )
}
