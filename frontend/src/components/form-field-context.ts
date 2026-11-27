import { createContext, useContext } from 'react'

export interface FieldControlProps {
  id?: string
  'aria-invalid'?: boolean
  'aria-describedby'?: string
  'aria-required'?: boolean
}

/** Lets custom controls rendered through a react-hook-form Controller pick up the FormField wiring. */
export const FormFieldContext = createContext<FieldControlProps>({})

export function useFieldControlProps(own: FieldControlProps = {}): FieldControlProps {
  const ctx = useContext(FormFieldContext)
  return {
    id: own.id ?? ctx.id,
    'aria-invalid': own['aria-invalid'] ?? ctx['aria-invalid'],
    'aria-describedby': own['aria-describedby'] ?? ctx['aria-describedby'],
    'aria-required': own['aria-required'] ?? ctx['aria-required'],
  }
}
