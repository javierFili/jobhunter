import { createContext, useContext } from 'react'
import type { UserDto } from '@/lib/types'

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous'

export interface LoginInput {
  email: string
  password: string
}

export interface RegisterInput extends LoginInput {
  fullName: string
}

export interface AuthContextValue {
  status: AuthStatus
  user: UserDto | null
  login: (input: LoginInput) => Promise<UserDto>
  register: (input: RegisterInput) => Promise<UserDto>
  logout: () => Promise<void>
  setUser: (user: UserDto) => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
