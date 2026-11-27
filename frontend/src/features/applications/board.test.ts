import { describe, expect, it } from 'vitest'
import { board, transitions } from '@/test/fixtures3'
import { applyMove, planDrop } from './board'

describe('Kanban drop planning', () => {
  it('reorders inside a column', () => {
    expect(planDrop(board(), transitions, 3, 2)).toEqual({ kind: 'reorder', status: 'FAVORITE', orderedIds: [3, 2] })
  })
  it('plans an allowed transition when dropped on another column', () => {
    expect(planDrop(board(), transitions, 2, 'col:READY_TO_APPLY')).toEqual({ kind: 'transition', id: 2, from: 'FAVORITE', to: 'READY_TO_APPLY' })
    expect(planDrop(board(), transitions, 2, 1)).toEqual({ kind: 'transition', id: 2, from: 'FAVORITE', to: 'READY_TO_APPLY' })
  })
  it('never transitions to APPLIED: it opens the mark-submitted flow', () => {
    expect(planDrop(board(), transitions, 1, 'col:APPLIED')).toEqual({ kind: 'mark-submitted', id: 1, from: 'READY_TO_APPLY' })
  })
  it('rejects transitions that are not allowed', () => {
    expect(planDrop(board(), transitions, 1, 'col:OFFER')).toEqual({ kind: 'not-allowed', from: 'READY_TO_APPLY', to: 'OFFER' })
  })
  it('does nothing when dropped outside or on itself', () => {
    expect(planDrop(board(), transitions, 1, null)).toEqual({ kind: 'none' })
    expect(planDrop(board(), transitions, 1, 1)).toEqual({ kind: 'none' })
  })
  it('applies an optimistic move', () => {
    const next = applyMove(board(), 2, 'READY_TO_APPLY')
    expect(next.columns.find((c) => c.status === 'FAVORITE')!.items.map((i) => i.id)).toEqual([3])
    expect(next.columns.find((c) => c.status === 'READY_TO_APPLY')!.items.map((i) => [i.id, i.status])).toEqual([
      [1, 'READY_TO_APPLY'],
      [2, 'READY_TO_APPLY'],
    ])
  })
})
