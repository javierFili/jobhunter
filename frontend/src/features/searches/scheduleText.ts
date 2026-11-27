import { scheduleLabels } from './labels'
import type { SavedSearchDto } from './types'

export function scheduleText(s: Pick<SavedSearchDto, 'scheduleType' | 'intervalMinutes' | 'dailyTime' | 'timezone'>): string {
  if (s.scheduleType === 'INTERVAL') {
    const m = s.intervalMinutes ?? 0
    return m % 60 === 0 ? `Cada ${m / 60} h` : `Cada ${m} min`
  }
  if (s.scheduleType === 'DAILY') return `Diaria a las ${s.dailyTime ?? '—'} (${(s.timezone ?? 'America/La_Paz').replace(/_/g, ' ')})`
  return scheduleLabels.MANUAL
}
