import { X } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import type { SkillRef } from '@/lib/types'
import { SkillSearch } from './SkillSearch'

interface SkillMultiPickerProps {
  id?: string
  value: SkillRef[]
  onChange: (value: SkillRef[]) => void
  'aria-describedby'?: string
  'aria-invalid'?: boolean
}

export function SkillMultiPicker({ id, value, onChange, ...aria }: SkillMultiPickerProps) {
  return (
    <div className="grid gap-2">
      {value.length > 0 && (
        <ul className="flex flex-wrap gap-1.5" aria-label="Habilidades seleccionadas">
          {value.map((s) => (
            <li key={s.id}>
              <Badge variant="secondary" className="h-6 gap-1 pr-1">
                {s.name}
                <button
                  type="button"
                  onClick={() => onChange(value.filter((v) => v.id !== s.id))}
                  className="rounded-full p-0.5 outline-none hover:bg-foreground/10 focus-visible:ring-2 focus-visible:ring-ring"
                  aria-label={`Quitar ${s.name}`}
                >
                  <X className="size-3" aria-hidden />
                </button>
              </Badge>
            </li>
          ))}
        </ul>
      )}
      <div>
        <SkillSearch
          id={id}
          excludeIds={value.map((s) => s.id)}
          onPick={(skill) => onChange([...value, { id: skill.id, name: skill.name }])}
          {...aria}
        />
      </div>
    </div>
  )
}
