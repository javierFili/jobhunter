import { useContext, useState } from 'react'
import { Loader2, Plus, Search } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@/components/ui/command'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { FormFieldContext, useFieldControlProps } from '@/components/form-field-context'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { skillCategoryLabels, toOptions } from '@/lib/labels'
import { errorMessage } from '@/lib/problem'
import type { CatalogSkill, SkillCategory } from '@/lib/types'
import { useCatalogSkills, useCreateCatalogSkill } from '../api'

interface SkillSearchProps {
  id?: string
  /** Text on the trigger button. */
  triggerLabel?: string
  excludeIds?: number[]
  onPick: (skill: CatalogSkill) => void
  'aria-describedby'?: string
  'aria-invalid'?: boolean
}

const categoryOptions = toOptions(skillCategoryLabels)

/** Autocomplete against /catalog/skills with the option to create a custom skill. */
export function SkillSearch({ id: ownId, triggerLabel = 'Agregar habilidad', excludeIds = [], onPick, ...aria }: SkillSearchProps) {
  const field = useFieldControlProps({ id: ownId, ...aria })
  // Inside a FormField the visible <label> names the control; standalone it needs its own name.
  const labelledByField = !!useContext(FormFieldContext).id
  const id = field.id
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState<SkillCategory>('OTHER')
  const term = useDebouncedValue(query.trim(), 250)
  const { data = [], isFetching } = useCatalogSkills(term, open)
  const create = useCreateCatalogSkill()

  const results = data.filter((s) => !excludeIds.includes(s.id))
  const exactExists = data.some((s) => s.name.toLowerCase() === term.toLowerCase())

  const pick = (skill: CatalogSkill) => {
    onPick(skill)
    setQuery('')
    setOpen(false)
  }

  const createSkill = () => {
    create.mutate(
      { name: term, category },
      {
        onSuccess: (skill) => {
          toast.success(`Habilidad «${skill.name}» agregada al catálogo`)
          pick(skill)
        },
        onError: (e) => toast.error(errorMessage(e)),
      },
    )
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          {...field}
          type="button"
          variant="outline"
          role="combobox"
          aria-expanded={open}
          aria-haspopup="listbox"
          aria-label={labelledByField ? undefined : triggerLabel}
        >
          <Plus aria-hidden />
          {triggerLabel}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-80 p-0" align="start">
        <Command shouldFilter={false}>
          <CommandInput
            value={query}
            onValueChange={setQuery}
            placeholder="Buscar habilidad (ej. React, Java)…"
            aria-label="Buscar habilidad"
          />
          <CommandList>
            {isFetching && results.length === 0 ? (
              <div className="flex items-center gap-2 px-3 py-4 text-sm text-muted-foreground">
                <Loader2 className="size-4 animate-spin" aria-hidden /> Buscando…
              </div>
            ) : (
              <CommandEmpty>
                <Search className="mx-auto mb-1 size-4 opacity-50" aria-hidden />
                Sin coincidencias en el catálogo.
              </CommandEmpty>
            )}
            {results.length > 0 && (
              <CommandGroup heading="Catálogo">
                {results.map((s) => (
                  <CommandItem key={s.id} value={String(s.id)} onSelect={() => pick(s)}>
                    <span className="truncate">{s.name}</span>
                    <span className="ml-auto text-xs text-muted-foreground">{skillCategoryLabels[s.category]}</span>
                  </CommandItem>
                ))}
              </CommandGroup>
            )}
          </CommandList>
          {term.length > 0 && !exactExists && (
            <div className="grid gap-2 border-t p-2">
              <p className="text-xs text-muted-foreground">
                ¿No está en el catálogo? Crea <strong className="text-foreground">«{term}»</strong>
              </p>
              <div className="flex gap-2">
                <label htmlFor={`${id ?? 'skill'}-new-category`} className="sr-only">
                  Categoría de la nueva habilidad
                </label>
                <select
                  id={`${id ?? 'skill'}-new-category`}
                  value={category}
                  onChange={(e) => setCategory(e.target.value as SkillCategory)}
                  className="h-8 min-w-0 flex-1 rounded-lg border border-input bg-background px-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                >
                  {categoryOptions.map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </select>
                <Button type="button" size="sm" onClick={createSkill} disabled={create.isPending}>
                  {create.isPending && <Loader2 className="animate-spin" aria-hidden />}
                  Crear
                </Button>
              </div>
            </div>
          )}
        </Command>
      </PopoverContent>
    </Popover>
  )
}
