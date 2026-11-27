import type { ReactNode } from 'react'
import { ChevronDown, FileText } from 'lucide-react'
import { Checkbox } from '@/components/ui/checkbox'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

interface ReviewCardProps {
  id: string
  title: string
  selected: boolean
  onSelectedChange: (selected: boolean) => void
  rawText: string | null
  hasErrors: boolean
  children: ReactNode
  badge?: ReactNode
}

export function ReviewCard({ id, title, selected, onSelectedChange, rawText, hasErrors, children, badge }: ReviewCardProps) {
  return (
    <article
      aria-label={title}
      className={cn(
        'grid gap-4 rounded-xl border bg-card p-4',
        !selected && 'opacity-60',
        hasErrors && selected && 'border-destructive/60',
      )}
    >
      <div className="flex items-start gap-3">
        <Checkbox id={`${id}-select`} checked={selected} onCheckedChange={(c) => onSelectedChange(c === true)} className="mt-0.5" aria-label={`Importar ${title}`} />
        <div className="flex min-w-0 flex-1 flex-wrap items-center gap-2">
          <label htmlFor={`${id}-select`} className="font-medium">
            {title}
          </label>
          {badge}
        </div>
      </div>
      {selected && (
        <div className="grid gap-4 lg:grid-cols-[1fr_minmax(0,18rem)]">
          <div className="grid gap-3">{children}</div>
          {rawText && (
            <Collapsible className="rounded-lg border bg-muted/30">
              <CollapsibleTrigger asChild>
                <Button variant="ghost" size="sm" className="w-full justify-between">
                  <span className="inline-flex items-center gap-1.5">
                    <FileText aria-hidden /> Texto original
                  </span>
                  <ChevronDown aria-hidden />
                </Button>
              </CollapsibleTrigger>
              <CollapsibleContent>
                <pre className="max-h-64 overflow-auto px-3 pb-3 text-xs whitespace-pre-wrap text-muted-foreground">{rawText}</pre>
              </CollapsibleContent>
            </Collapsible>
          )}
        </div>
      )}
    </article>
  )
}
