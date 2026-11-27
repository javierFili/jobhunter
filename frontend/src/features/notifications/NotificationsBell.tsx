import { useState } from 'react'
import { useNavigate } from 'react-router'
import { Bell, BellRing, Briefcase, CalendarClock, CheckCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { formatRelative } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useMarkAllRead, useMarkRead, useNotifications, useUnreadCount, type NotificationDto } from './api'

export function NotificationsBell() {
  const [open, setOpen] = useState(false)
  const navigate = useNavigate()
  const unread = useUnreadCount()
  const list = useNotifications(open)
  const markRead = useMarkRead()
  const markAll = useMarkAllRead()
  const count = unread.data?.count ?? 0

  const openNotification = (n: NotificationDto) => {
    if (!n.readAt) markRead.mutate(n.id)
    if (n.link) {
      setOpen(false)
      // Internal links navigate in-app; anything else is ignored for safety.
      if (n.link.startsWith('/')) navigate(n.link)
    }
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button variant="ghost" size="icon" className="relative" aria-label={count ? `Notificaciones: ${count} sin leer` : 'Notificaciones'}>
          <Bell aria-hidden />
          {count > 0 && (
            <span aria-hidden className="absolute -top-0.5 -right-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] font-semibold text-white">
              {count > 99 ? '99+' : count}
            </span>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-80 p-0">
        <div className="flex items-center justify-between border-b px-3 py-2">
          <h2 className="text-sm font-semibold">Notificaciones</h2>
          <Button variant="ghost" size="xs" disabled={count === 0 || markAll.isPending} onClick={() => markAll.mutate()}>
            <CheckCheck aria-hidden /> Marcar todas como leídas
          </Button>
        </div>
        {list.isLoading ? (
          <p className="px-3 py-6 text-center text-sm text-muted-foreground">Cargando…</p>
        ) : !list.data?.content.length ? (
          <p className="px-3 py-6 text-center text-sm text-muted-foreground">No tienes notificaciones.</p>
        ) : (
          <ul className="max-h-96 overflow-y-auto" aria-label="Lista de notificaciones">
            {list.data.content.map((n) => (
              <li key={n.id} className="border-b last:border-0">
                <button
                  type="button"
                  onClick={() => openNotification(n)}
                  className={cn('grid w-full gap-0.5 px-3 py-2 text-left text-sm outline-none hover:bg-muted focus-visible:bg-muted', !n.readAt && 'bg-primary/5')}
                >
                  <span className="flex items-center gap-2 font-medium">
                    {!n.readAt && <span className="size-2 shrink-0 rounded-full bg-primary" aria-label="Sin leer" />}
                    {n.type === 'NEW_JOBS' ? (
                      <Briefcase className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                    ) : n.type === 'FOLLOW_UP' ? (
                      <CalendarClock className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                    ) : (
                      <BellRing className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                    )}
                    {n.title}
                  </span>
                  {n.body && <span className="line-clamp-2 text-muted-foreground">{n.body}</span>}
                  <span className="text-xs text-muted-foreground">{formatRelative(n.createdAt)}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </PopoverContent>
    </Popover>
  )
}
