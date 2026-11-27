import { useRef, useState, type DragEvent } from 'react'
import { useNavigate } from 'react-router'
import { FileUp, Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { ApiError, errorMessage } from '@/lib/problem'
import { cn } from '@/lib/utils'
import { useUploadResume } from '../api'
import { uploadErrorMessages } from '../labels'
import { validateResumeFile } from './validateResumeFile'

export function UploadDropzone() {
  const navigate = useNavigate()
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const upload = useUploadResume()

  const handleFile = (file: File | undefined) => {
    if (!file) return
    const invalid = validateResumeFile(file)
    setError(invalid)
    if (invalid) return
    upload.mutate(file, {
      onSuccess: (u) => {
        toast.success('CV procesado. Revisa lo detectado antes de importarlo.')
        navigate(`/cvs/uploads/${u.id}`)
      },
      onError: (e) => {
        const msg = e instanceof ApiError && e.code && uploadErrorMessages[e.code] ? uploadErrorMessages[e.code] : errorMessage(e)
        setError(msg)
        toast.error(msg)
      },
    })
  }

  const onDrop = (e: DragEvent) => {
    e.preventDefault()
    setDragging(false)
    handleFile(e.dataTransfer.files?.[0])
  }

  return (
    <div className="grid gap-2">
      <div
        onDragOver={(e) => {
          e.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={cn(
          'flex flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed px-6 py-10 text-center transition-colors',
          dragging ? 'border-primary bg-primary/5' : 'border-border',
        )}
      >
        <div className="flex size-11 items-center justify-center rounded-full bg-primary/10 text-primary">
          {upload.isPending ? <Loader2 className="size-5 animate-spin" aria-hidden /> : <FileUp className="size-5" aria-hidden />}
        </div>
        <div>
          <p className="font-medium">{upload.isPending ? 'Procesando tu CV…' : 'Arrastra tu CV aquí'}</p>
          <p className="text-sm text-muted-foreground">PDF o DOCX, máximo 5 MB.</p>
        </div>
        <input
          ref={inputRef}
          id="resume-file"
          type="file"
          accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
          className="sr-only"
          aria-label="Seleccionar archivo de CV"
          aria-describedby={error ? 'resume-file-error' : undefined}
          onChange={(e) => {
            handleFile(e.target.files?.[0])
            e.target.value = ''
          }}
          disabled={upload.isPending}
        />
        <Button type="button" variant="outline" onClick={() => inputRef.current?.click()} disabled={upload.isPending}>
          Elegir archivo
        </Button>
      </div>
      {error && (
        <p id="resume-file-error" role="alert" className="text-sm font-medium text-destructive">
          {error}
        </p>
      )}
    </div>
  )
}
