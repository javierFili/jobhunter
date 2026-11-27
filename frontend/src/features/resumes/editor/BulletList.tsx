import { ArrowDown, ArrowUp, Check, Plus, Trash2 } from 'lucide-react'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { cn } from '@/lib/utils'
import { bulletStatusLabels } from '../labels'
import type { Bullet } from '../types'

interface BulletListProps {
  /** Used in accessible labels, e.g. "Backend Developer". */
  owner: string
  bullets: Bullet[]
  onChange: (bullets: Bullet[]) => void
}

const statusTone = { GROUNDED: 'success', SUGGESTED: 'warning', USER_EDITED: 'violet' } as const

export function BulletList({ owner, bullets, onChange }: BulletListProps) {
  const set = (i: number, b: Bullet) => onChange(bullets.map((x, j) => (j === i ? b : x)))
  const move = (from: number, to: number) => {
    const next = [...bullets]
    const [b] = next.splice(from, 1)
    next.splice(to, 0, b)
    onChange(next)
  }
  const remove = (i: number) => onChange(bullets.filter((_, j) => j !== i))

  return (
    <div className="grid gap-2">
      <ol className="grid gap-2" aria-label={`Viñetas de ${owner}`}>
        {bullets.map((b, i) => {
          const suggested = b.status === 'SUGGESTED'
          const label = `Viñeta ${i + 1} de ${owner}`
          return (
            <li
              key={i}
              data-status={b.status}
              className={cn(
                'grid gap-2 rounded-lg border p-2',
                suggested && 'border-amber-400 bg-amber-50 dark:border-amber-700 dark:bg-amber-950/60',
              )}
            >
              <div className="flex flex-wrap items-center justify-between gap-2">
                <StatusBadge tone={statusTone[b.status]}>{bulletStatusLabels[b.status]}</StatusBadge>
                <div className="flex items-center gap-1">
                  {suggested && (
                    <>
                      <Button type="button" size="xs" variant="outline" onClick={() => set(i, { ...b, status: 'USER_EDITED', note: null })} aria-label={`Confirmar ${label}`}>
                        <Check aria-hidden /> Confirmar
                      </Button>
                      <Button type="button" size="xs" variant="destructive" onClick={() => remove(i)} aria-label={`Eliminar sugerencia ${label}`}>
                        Eliminar
                      </Button>
                    </>
                  )}
                  <Button type="button" variant="ghost" size="icon-xs" disabled={i === 0} onClick={() => move(i, i - 1)} aria-label={`Subir ${label}`}>
                    <ArrowUp aria-hidden />
                  </Button>
                  <Button type="button" variant="ghost" size="icon-xs" disabled={i === bullets.length - 1} onClick={() => move(i, i + 1)} aria-label={`Bajar ${label}`}>
                    <ArrowDown aria-hidden />
                  </Button>
                  {!suggested && (
                    <Button type="button" variant="ghost" size="icon-xs" onClick={() => remove(i)} aria-label={`Quitar ${label}`}>
                      <Trash2 aria-hidden />
                    </Button>
                  )}
                </div>
              </div>
              {suggested && b.note && (
                <p className="text-xs text-amber-900 dark:text-amber-200">
                  <span className="font-semibold">Motivo: </span>
                  {b.note}
                </p>
              )}
              <Textarea
                aria-label={label}
                rows={2}
                value={b.text}
                // Any manual edit makes the bullet the user's responsibility.
                onChange={(e) => set(i, { ...b, text: e.target.value, status: 'USER_EDITED', note: null })}
              />
            </li>
          )
        })}
      </ol>
      <div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => onChange([...bullets, { text: '', sourceRefs: [], status: 'USER_EDITED' }])}
          aria-label={`Agregar viñeta a ${owner}`}
        >
          <Plus aria-hidden /> Agregar viñeta
        </Button>
      </div>
    </div>
  )
}
