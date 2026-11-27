import type { ReactNode } from 'react'
import { Slot } from 'radix-ui'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'
import { FormFieldContext } from './form-field-context'

interface FormFieldProps {
  id: string
  label: ReactNode
  error?: string
  hint?: ReactNode
  required?: boolean
  className?: string
  /** A single focusable control; it receives id / aria-invalid / aria-describedby. */
  children: ReactNode
}

/** Label + control + hint + error with correct ARIA wiring. */
export function FormField({ id, label, error, hint, required, className, children }: FormFieldProps) {
  const hintId = hint ? `${id}-hint` : undefined
  const errorId = error ? `${id}-error` : undefined
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined
  const control = {
    id,
    'aria-invalid': error ? true : undefined,
    'aria-describedby': describedBy,
    'aria-required': required || undefined,
  }
  return (
    <div className={cn('grid gap-1.5', className)}>
      <Label htmlFor={id}>
        {label}
        {required && (
          <span className="text-destructive" aria-hidden>
            *
          </span>
        )}
      </Label>
      <FormFieldContext.Provider value={control}>
        <Slot.Root {...control}>{children}</Slot.Root>
      </FormFieldContext.Provider>
      {hint && (
        <p id={hintId} className="text-xs text-muted-foreground">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} role="alert" className="text-xs font-medium text-destructive">
          {error}
        </p>
      )}
    </div>
  )
}
