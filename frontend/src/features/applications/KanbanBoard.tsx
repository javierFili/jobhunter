import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  closestCorners,
  useDroppable,
  useSensor,
  useSensors,
  type Announcements,
  type DragEndEvent,
} from '@dnd-kit/core'
import { SortableContext, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { useQueryClient } from '@tanstack/react-query'
import { CalendarClock, GripVertical } from 'lucide-react'
import { toast } from 'sonner'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { StatusBadge } from '@/components/StatusBadge'
import { MatchScoreChip } from '@/features/matching/MatchScoreChip'
import { formatDate } from '@/lib/format'
import { ApiError, errorMessage } from '@/lib/problem'
import { cn } from '@/lib/utils'
import { appKeys, useBoard, useReorder, useTransition, useTransitions } from './api'
import { applyMove, columnId, findColumn, planDrop } from './board'
import { isOverdue, statusLabels } from './labels'
import { MarkSubmittedDialog } from './MarkSubmittedDialog'
import { MoveMenu } from './MoveMenu'
import type { ApplicationDto, BoardDto } from './types'

function FollowUpBadge({ date }: { date: string | null }) {
  if (!date) return null
  const overdue = isOverdue(date)
  return (
    <StatusBadge tone={overdue ? 'danger' : 'neutral'} icon={<CalendarClock aria-hidden />}>
      {overdue ? 'Seguimiento vencido' : 'Seguimiento'} {formatDate(date)}
    </StatusBadge>
  )
}

function BoardCard({ app, onMarkSubmitted }: { app: ApplicationDto; onMarkSubmitted: (a: ApplicationDto) => void }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: app.id })
  return (
    <li
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={cn('grid gap-1.5 rounded-lg border bg-card p-2.5 text-sm shadow-xs', isDragging && 'opacity-60 ring-2 ring-primary')}
      aria-label={`${app.positionTitle} en ${app.companyName}`}
    >
      <div className="flex items-start gap-1">
        <button
          type="button"
          className="mt-0.5 cursor-grab rounded p-0.5 text-muted-foreground outline-none hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring"
          aria-label={`Arrastrar ${app.positionTitle}`}
          {...attributes}
          {...listeners}
        >
          <GripVertical className="size-4" aria-hidden />
        </button>
        <div className="min-w-0 flex-1">
          <Link to={`/applications/${app.id}`} className="font-medium hover:underline">
            {app.positionTitle}
          </Link>
          <p className="truncate text-xs text-muted-foreground">{app.companyName}</p>
        </div>
        <MoveMenu application={app} onMarkSubmitted={() => onMarkSubmitted(app)} />
      </div>
      <div className="flex flex-wrap gap-1">
        {app.match && <MatchScoreChip score={app.match.scoreTotal} classification={app.match.classification} />}
        <FollowUpBadge date={app.followUpAt} />
      </div>
    </li>
  )
}

function Column({ status, items, children }: { status: ApplicationDto['status']; items: ApplicationDto[]; children: ReactNode }) {
  const { setNodeRef, isOver } = useDroppable({ id: columnId(status) })
  return (
    <section
      ref={setNodeRef}
      aria-label={`Columna ${statusLabels[status]}`}
      className={cn('flex w-64 shrink-0 flex-col gap-2 rounded-xl border bg-muted/40 p-2', isOver && 'ring-2 ring-primary/50')}
    >
      <h3 className="flex items-center justify-between px-1 text-sm font-semibold">
        {statusLabels[status]} <span className="text-xs font-normal text-muted-foreground">{items.length}</span>
      </h3>
      <SortableContext items={items.map((i) => i.id)} strategy={verticalListSortingStrategy}>
        <ul className="grid min-h-16 content-start gap-2">{children}</ul>
      </SortableContext>
    </section>
  )
}

export function KanbanBoard() {
  const qc = useQueryClient()
  const { data, isLoading, error, refetch } = useBoard()
  const transitions = useTransitions()
  const reorder = useReorder()
  const transition = useTransition()
  const [submitting, setSubmitting] = useState<ApplicationDto | null>(null)
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }), useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }))

  if (isLoading) return <LoadingState />
  if (error || !data) return <ErrorState error={error} onRetry={() => void refetch()} />
  const board = data

  const label = (id: number | string) => {
    const col = findColumn(board, id)
    const app = board.columns.flatMap((c) => c.items).find((i) => i.id === Number(id))
    return app ? `${app.positionTitle} (${col ? statusLabels[col] : ''})` : col ? `columna ${statusLabels[col]}` : ''
  }
  const announcements: Announcements = {
    onDragStart: ({ active }) => `Tomaste ${label(active.id)}.`,
    onDragOver: ({ over }) => (over ? `Sobre ${label(over.id)}.` : 'Fuera de las columnas.'),
    onDragEnd: ({ over }) => (over ? `Soltaste sobre ${label(over.id)}.` : 'Movimiento cancelado.'),
    onDragCancel: () => 'Movimiento cancelado.',
  }

  const onDragEnd = ({ active, over }: DragEndEvent) => {
    const plan = planDrop(board, transitions.data ?? {}, Number(active.id), over ? (typeof over.id === 'string' ? over.id : Number(over.id)) : null)
    const set = (b: BoardDto) => qc.setQueryData(appKeys.board, b)
    switch (plan.kind) {
      case 'reorder':
        set(applyMove(board, Number(active.id), plan.status, plan.orderedIds))
        reorder.mutate({ status: plan.status, orderedIds: plan.orderedIds }, { onError: (e) => toast.error(errorMessage(e)) })
        break
      case 'transition':
        set(applyMove(board, plan.id, plan.to))
        transition.mutate(
          { id: plan.id, toStatus: plan.to },
          {
            onError: (e) => {
              set(board)
              toast.error(e instanceof ApiError && e.code === 'INVALID_TRANSITION' ? 'Ese cambio de estado no está permitido.' : errorMessage(e))
            },
          },
        )
        break
      case 'mark-submitted':
        setSubmitting(board.columns.flatMap((c) => c.items).find((i) => i.id === plan.id) ?? null)
        break
      case 'not-allowed':
        toast.error(`No se puede pasar de «${statusLabels[plan.from]}» a «${statusLabels[plan.to]}».`)
        break
    }
  }

  return (
    <>
      <p className="mb-2 text-xs text-muted-foreground">
        Arrastra las tarjetas entre columnas (con teclado: enfoca el asa, Espacio para tomar, flechas para mover, Espacio para soltar) o usa el menú «Cambiar estado».
      </p>
      <DndContext
        sensors={sensors}
        collisionDetection={closestCorners}
        onDragEnd={onDragEnd}
        accessibility={{
          announcements,
          screenReaderInstructions: { draggable: 'Para tomar una tarjeta presiona Espacio. Usa las flechas para moverla y Espacio para soltarla; Escape cancela.' },
        }}
      >
        <div className="-mx-4 overflow-x-auto px-4 pb-2 md:mx-0 md:px-0">
          <div className="flex gap-3">
            {board.columns.map((c) => (
              <Column key={c.status} status={c.status} items={c.items}>
                {c.items.map((a) => (
                  <BoardCard key={a.id} app={a} onMarkSubmitted={setSubmitting} />
                ))}
              </Column>
            ))}
          </div>
        </div>
      </DndContext>
      <MarkSubmittedDialog application={submitting} onClose={() => setSubmitting(null)} />
    </>
  )
}
