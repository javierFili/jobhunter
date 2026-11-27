import { z } from 'zod'

export const PASSWORD_MIN = 10

export const loginSchema = z.object({
  email: z.string().trim().min(1, 'Ingresa tu correo electrónico').pipe(z.email('Correo electrónico inválido')),
  password: z.string().min(1, 'Ingresa tu contraseña'),
})

export const registerSchema = z
  .object({
    fullName: z.string().trim().min(2, 'Ingresa tu nombre completo').max(120, 'Máximo 120 caracteres'),
    email: z.string().trim().min(1, 'Ingresa tu correo electrónico').pipe(z.email('Correo electrónico inválido')),
    password: z
      .string()
      .min(PASSWORD_MIN, `La contraseña debe tener al menos ${PASSWORD_MIN} caracteres`)
      .max(128, 'Máximo 128 caracteres'),
    confirmPassword: z.string(),
  })
  .refine((v) => v.password === v.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Las contraseñas no coinciden',
  })

export type LoginValues = z.infer<typeof loginSchema>
export type RegisterValues = z.infer<typeof registerSchema>
