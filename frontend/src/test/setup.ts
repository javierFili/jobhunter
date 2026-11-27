import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterAll, afterEach, beforeAll, vi } from 'vitest'
import { setAccessToken } from '@/lib/api'
import { resetDb } from './db'
import { server } from './server'

// Lazy-loaded routes need a bit more time on the first (cold) transform.
configure({ asyncUtilTimeout: 15_000 })

// ---- jsdom polyfills needed by Radix / cmdk / Recharts ----
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver ??= ResizeObserverStub as unknown as typeof ResizeObserver
Element.prototype.scrollIntoView ??= vi.fn()
Element.prototype.hasPointerCapture ??= () => false
Element.prototype.releasePointerCapture ??= () => {}
Element.prototype.setPointerCapture ??= () => {}
URL.createObjectURL ??= vi.fn(() => 'blob:test-object-url')
URL.revokeObjectURL ??= vi.fn()
if (!window.matchMedia) {
  window.matchMedia = (query: string) =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList
}

beforeAll(async () => {
  server.listen({ onUnhandledFrame: 'error' })
  // Warm the lazily loaded route modules so cold transforms don't race findBy* timeouts.
  await Promise.all([
    import('@/features/dashboard/DashboardPage'),
    import('@/features/profile/ProfilePage'),
    import('@/features/settings/SettingsPage'),
  ])
})
afterEach(() => {
  cleanup()
  server.resetHandlers()
  resetDb()
  setAccessToken(null)
  localStorage.clear()
  document.documentElement.classList.remove('dark')
})
afterAll(() => server.close())
