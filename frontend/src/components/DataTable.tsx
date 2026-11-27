import type { ReactNode } from 'react'
import { Checkbox } from '@/components/ui/checkbox'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { cn } from '@/lib/utils'

export interface Column<T> {
  id: string
  header: ReactNode
  cell: (row: T) => ReactNode
  className?: string
}

interface DataTableProps<T, K extends string | number> {
  columns: Column<T>[]
  rows: T[]
  getRowId: (row: T) => K
  /** Accessible label for the row used by selection checkboxes. */
  getRowLabel?: (row: T) => string
  caption?: string
  selectable?: boolean
  selected?: K[]
  onSelectedChange?: (ids: K[]) => void
  empty?: ReactNode
}

export function DataTable<T, K extends string | number>({
  columns,
  rows,
  getRowId,
  getRowLabel,
  caption,
  selectable = false,
  selected = [],
  onSelectedChange,
  empty,
}: DataTableProps<T, K>) {
  if (rows.length === 0 && empty) return <>{empty}</>
  const allIds = rows.map(getRowId)
  const allSelected = allIds.length > 0 && allIds.every((id) => selected.includes(id))
  const someSelected = !allSelected && allIds.some((id) => selected.includes(id))

  const toggle = (id: K, checked: boolean) =>
    onSelectedChange?.(checked ? [...selected, id] : selected.filter((s) => s !== id))

  return (
    <div className="overflow-x-auto rounded-xl border">
      <Table>
        {caption && <caption className="sr-only">{caption}</caption>}
        <TableHeader>
          <TableRow className="bg-muted/40 hover:bg-muted/40">
            {selectable && (
              <TableHead className="w-10">
                <Checkbox
                  aria-label="Seleccionar todo"
                  checked={allSelected ? true : someSelected ? 'indeterminate' : false}
                  onCheckedChange={(c) => onSelectedChange?.(c === true ? allIds : [])}
                />
              </TableHead>
            )}
            {columns.map((c) => (
              <TableHead key={c.id} className={c.className}>
                {c.header}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => {
            const id = getRowId(row)
            const isSelected = selected.includes(id)
            return (
              <TableRow key={String(id)} data-state={isSelected ? 'selected' : undefined}>
                {selectable && (
                  <TableCell>
                    <Checkbox
                      aria-label={`Seleccionar ${getRowLabel?.(row) ?? String(id)}`}
                      checked={isSelected}
                      onCheckedChange={(c) => toggle(id, c === true)}
                    />
                  </TableCell>
                )}
                {columns.map((c) => (
                  <TableCell key={c.id} className={cn('align-middle', c.className)}>
                    {c.cell(row)}
                  </TableCell>
                ))}
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
    </div>
  )
}
