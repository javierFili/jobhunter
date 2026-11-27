import { Crosshair } from 'lucide-react'
import { cn } from '@/lib/utils'

export function Logo({ className, showText = true }: { className?: string; showText?: boolean }) {
  return (
    <span className={cn('flex items-center gap-2 font-semibold', className)}>
      <span className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
        <Crosshair className="size-4" aria-hidden />
      </span>
      {showText && (
        <span className="truncate">
          JobHunter <span className="text-primary">AI</span>
        </span>
      )}
    </span>
  )
}
