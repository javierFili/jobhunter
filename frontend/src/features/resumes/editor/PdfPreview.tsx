import { Loader2, RefreshCw } from 'lucide-react'
import { ErrorState } from '@/components/ErrorState'
import { Button } from '@/components/ui/button'
import { useResumePdf } from '../api'
import type { TemplateId } from '../types'
import { useObjectUrl } from './useObjectUrl'

export function PdfPreview({ resumeId, versionNo, template }: { resumeId: number; versionNo: number; template: TemplateId }) {
  const { data, isFetching, error, refetch } = useResumePdf(resumeId, versionNo, template)
  const url = useObjectUrl(data)
  return (
    <div className="grid gap-2">
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs text-muted-foreground">Vista previa de la versión guardada v{versionNo}.</p>
        <Button type="button" variant="outline" size="sm" onClick={() => void refetch()} disabled={isFetching}>
          {isFetching ? <Loader2 className="animate-spin" aria-hidden /> : <RefreshCw aria-hidden />}
          Actualizar vista previa
        </Button>
      </div>
      {error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : url ? (
        <iframe title="Vista previa del CV en PDF" src={url} className="h-[75svh] w-full rounded-lg border bg-white" />
      ) : (
        <div className="flex h-[75svh] items-center justify-center rounded-lg border" role="status">
          <Loader2 className="size-5 animate-spin text-muted-foreground" aria-hidden />
          <span className="sr-only">Generando PDF…</span>
        </div>
      )}
    </div>
  )
}
