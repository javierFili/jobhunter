import { Check } from 'lucide-react'
import { cn } from '@/lib/utils'

interface ChipMultiSelectProps<T extends string> {
  /** Accessible group name. */
  label: string
  options: { value: T; label: string }[]
  value: T[]
  onChange: (value: T[]) => void
  id?: string
}

/** Toggle-chip multi-select (role=group of aria-pressed buttons), fully keyboard accessible. */
export function ChipMultiSelect<T extends string>({ label, options, value, onChange, id }: ChipMultiSelectProps<T>) {
  return (
    <div role="group" aria-label={label} id={id} className="flex flex-wrap gap-2">
      {options.map((o) => {
        const active = value.includes(o.value)
        return (
          <button
            key={o.value}
            type="button"
            aria-pressed={active}
            onClick={() => onChange(active ? value.filter((v) => v !== o.value) : [...value, o.value])}
            className={cn(
              'inline-flex h-8 items-center gap-1.5 rounded-full border px-3 text-sm transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50',
              active
                ? 'border-primary bg-primary text-primary-foreground'
                : 'border-border bg-background hover:bg-muted',
            )}
          >
            {active && <Check className="size-3.5" aria-hidden />}
            {o.label}
          </button>
        )
      })}
    </div>
  )
}
