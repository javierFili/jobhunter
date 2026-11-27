import type { ComponentProps } from 'react'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { cn } from '@/lib/utils'
import { useFieldControlProps } from './form-field-context'

const NONE = '__none__'

type TriggerProps = Omit<ComponentProps<typeof SelectTrigger>, 'onChange' | 'value' | 'children'>

interface SelectFieldProps<T extends string> extends TriggerProps {
  value: T | '' | null | undefined
  onChange: (value: T | '') => void
  options: { value: T; label: string }[]
  placeholder?: string
  /** Adds an explicit "none" option mapped to ''. */
  noneLabel?: string
  disabled?: boolean
}

/** shadcn Select wired for forms: '' represents "no value"; extra props go to the trigger (id, aria-*). */
export function SelectField<T extends string>({
  value,
  onChange,
  options,
  placeholder = 'Seleccionar…',
  noneLabel,
  disabled,
  className,
  ...triggerProps
}: SelectFieldProps<T>) {
  const { id, 'aria-invalid': ariaInvalid, 'aria-describedby': ariaDescribedBy, 'aria-required': ariaRequired, ...rest } = triggerProps
  const field = useFieldControlProps({ id, 'aria-invalid': ariaInvalid === true || ariaInvalid === 'true' ? true : undefined, 'aria-describedby': ariaDescribedBy, 'aria-required': ariaRequired === true || ariaRequired === 'true' ? true : undefined })
  const current = value ? value : noneLabel ? NONE : ''
  return (
    <Select value={current} onValueChange={(v) => onChange(v === NONE ? '' : (v as T))} disabled={disabled}>
      <SelectTrigger className={cn('w-full', className)} {...rest} {...field}>
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent>
        {noneLabel && <SelectItem value={NONE}>{noneLabel}</SelectItem>}
        {options.map((o) => (
          <SelectItem key={o.value} value={o.value}>
            {o.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
