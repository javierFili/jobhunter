import { useAuth } from '@/features/auth/auth-context'
import { useTheme } from '@/lib/theme-context'
import type { Theme } from '@/lib/types'
import { useUpdateMe } from './api'

export const themeLabels: Record<Theme, string> = { light: 'Claro', dark: 'Oscuro', system: 'Sistema' }

/** Applies the theme immediately (localStorage) and persists it on the server when logged in. */
export function useThemePreference() {
  const { theme, resolvedTheme, setTheme } = useTheme()
  const { status } = useAuth()
  const updateMe = useUpdateMe()
  const change = (next: Theme) => {
    setTheme(next)
    // Server persistence is best-effort; localStorage already keeps the choice.
    if (status === 'authenticated') updateMe.mutate({ theme: next })
  }
  return { theme, resolvedTheme, setTheme: change }
}
