import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router'
import { QueryClient } from '@tanstack/react-query'
import { AppProviders } from '@/app/AppProviders'
import { AppRoutes } from '@/app/AppRoutes'

function LocationProbe() {
  const location = useLocation()
  return <div data-testid="location" hidden>{location.pathname + location.search}</div>
}

/** Renders the full app (providers + routes) at the given URL against the MSW backend. */
export function renderApp(route = '/') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity }, mutations: { retry: false } },
  })
  const user = userEvent.setup()
  const utils = render(
    <MemoryRouter initialEntries={[route]}>
      <AppProviders queryClient={queryClient}>
        <AppRoutes />
        <LocationProbe />
      </AppProviders>
    </MemoryRouter>,
  )
  return { ...utils, user, queryClient }
}
