import { useState } from 'react'
import { Check, ChevronsUpDown, X } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@/components/ui/command'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/utils'
import { useFieldControlProps } from './form-field-context'

interface SearchableMultiSelectProps {
  id?: string
  label: string
  placeholder?: string
  options: { value: string; label: string }[]
  value: string[]
  onChange: (value: string[]) => void
  disabledValues?: string[]
}

/** Popover + searchable list with multiple selection; selected values shown as removable badges. */
export function SearchableMultiSelect({
  id,
  label,
  placeholder = 'Seleccionar…',
  options,
  value,
  onChange,
  disabledValues = [],
}: SearchableMultiSelectProps) {
  const [open, setOpen] = useState(false)
  const field = useFieldControlProps({ id })
  const labelOf = (v: string) => options.find((o) => o.value === v)?.label ?? v
  const toggle = (v: string) => onChange(value.includes(v) ? value.filter((x) => x !== v) : [...value, v])

  return (
    <div className="grid gap-2">
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button
            {...field}
            type="button"
            variant="outline"
            role="combobox"
            aria-expanded={open}
            aria-label={label}
            className="h-9 w-full justify-between font-normal"
          >
            <span className="truncate text-muted-foreground">
              {value.length ? `${value.length} seleccionado(s)` : placeholder}
            </span>
            <ChevronsUpDown className="opacity-50" aria-hidden />
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-(--radix-popover-trigger-width) min-w-64 p-0" align="start">
          <Command>
            <CommandInput placeholder="Buscar…" aria-label={`Buscar en ${label}`} />
            <CommandList>
              <CommandEmpty>Sin resultados.</CommandEmpty>
              <CommandGroup>
                {options.map((o) => {
                  const selected = value.includes(o.value)
                  const disabled = disabledValues.includes(o.value)
                  return (
                    <CommandItem
                      key={o.value}
                      value={`${o.label} ${o.value}`}
                      disabled={disabled}
                      onSelect={() => toggle(o.value)}
                      aria-selected={selected}
                    >
                      <Check className={cn('size-4', selected ? 'opacity-100' : 'opacity-0')} aria-hidden />
                      {o.label}
                      {disabled && <span className="ml-auto text-xs text-muted-foreground">en otra lista</span>}
                    </CommandItem>
                  )
                })}
              </CommandGroup>
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>
      {value.length > 0 && (
        <ul className="flex flex-wrap gap-1.5" aria-label={`${label}: seleccionados`}>
          {value.map((v) => (
            <li key={v}>
              <Badge variant="secondary" className="h-6 gap-1 pr-1">
                {labelOf(v)}
                <button
                  type="button"
                  onClick={() => toggle(v)}
                  className="rounded-full p-0.5 hover:bg-foreground/10 focus-visible:ring-2 focus-visible:ring-ring outline-none"
                  aria-label={`Quitar ${labelOf(v)}`}
                >
                  <X className="size-3" aria-hidden />
                </button>
              </Badge>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
