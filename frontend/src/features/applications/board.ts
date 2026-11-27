import type { AppStatus, ApplicationDto, BoardDto, Transitions } from './types'

export type DropPlan =
  | { kind: 'none' }
  | { kind: 'reorder'; status: AppStatus; orderedIds: number[] }
  | { kind: 'transition'; id: number; from: AppStatus; to: AppStatus }
  | { kind: 'mark-submitted'; id: number; from: AppStatus }
  | { kind: 'not-allowed'; from: AppStatus; to: AppStatus }

/** Droppable ids: application ids are numbers; empty-column targets are "col:STATUS". */
export const columnId = (s: AppStatus) => `col:${s}`

export function findColumn(board: BoardDto, id: number | string): AppStatus | null {
  if (typeof id === 'string' && id.startsWith('col:')) return id.slice(4) as AppStatus
  return board.columns.find((c) => c.items.some((i) => i.id === Number(id)))?.status ?? null
}

/** Pure decision for a drag end, unit-tested independently of dnd-kit. */
export function planDrop(board: BoardDto, transitions: Transitions, activeId: number, overId: number | string | null): DropPlan {
  if (overId == null) return { kind: 'none' }
  const from = findColumn(board, activeId)
  const to = findColumn(board, overId)
  if (!from || !to) return { kind: 'none' }
  if (from === to) {
    const items = board.columns.find((c) => c.status === from)!.items.map((i) => i.id)
    const oldIndex = items.indexOf(activeId)
    const newIndex = typeof overId === 'number' ? items.indexOf(overId) : items.length - 1
    if (oldIndex < 0 || newIndex < 0 || oldIndex === newIndex) return { kind: 'none' }
    const next = [...items]
    next.splice(oldIndex, 1)
    next.splice(newIndex, 0, activeId)
    return { kind: 'reorder', status: from, orderedIds: next }
  }
  if (to === 'APPLIED') return { kind: 'mark-submitted', id: activeId, from }
  if (!(transitions[from] ?? []).includes(to)) return { kind: 'not-allowed', from, to }
  return { kind: 'transition', id: activeId, from, to }
}

/** Optimistic board update after a move/reorder. */
export function applyMove(board: BoardDto, id: number, to: AppStatus, orderedIds?: number[]): BoardDto {
  let moved: ApplicationDto | undefined
  const without = board.columns.map((c) => {
    const item = c.items.find((i) => i.id === id)
    if (item) moved = item
    return { ...c, items: c.items.filter((i) => i.id !== id) }
  })
  if (!moved) return board
  return {
    columns: without.map((c) => {
      if (c.status !== to) return c
      const items = [...c.items, { ...moved!, status: to }]
      if (orderedIds) items.sort((a, b) => orderedIds.indexOf(a.id) - orderedIds.indexOf(b.id))
      return { ...c, items }
    }),
  }
}
