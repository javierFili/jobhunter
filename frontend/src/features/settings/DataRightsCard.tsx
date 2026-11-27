import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { Download, Loader2, ShieldAlert, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { FormField } from '@/components/FormField'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/features/auth/auth-context'
import { downloadBlob } from '@/features/resumes/editor/useObjectUrl'
import { api, apiRequest } from '@/lib/api'
import { ApiError, errorMessage } from '@/lib/problem'

const DELETE_CONFIRMATION = 'ELIMINAR'

function ExportSection() {
  const [pending, setPending] = useState(false)
  const run = async () => {
    setPending(true)
    try {
      const blob = await api.blob('/me/export')
      downloadBlob(blob, `jobhunter-mis-datos-${new Date().toISOString().slice(0, 10)}.zip`)
      toast.success('Exportación descargada')
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      setPending(false)
    }
  }
  return (
    <div className="grid gap-2">
      <h3 className="font-medium">Exportar mis datos</h3>
      <p className="text-sm text-muted-foreground">Descarga un archivo ZIP con tu perfil, CV, cartas, postulaciones y configuración.</p>
      <div>
        <Button variant="outline" onClick={() => void run()} disabled={pending}>
          {pending ? <Loader2 className="animate-spin" aria-hidden /> : <Download aria-hidden />}
          Exportar mis datos
        </Button>
      </div>
    </div>
  )
}

function DeleteSection() {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const [confirm, setConfirm] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const ready = confirm === DELETE_CONFIRMATION && password.length > 0

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!ready) return
    setPending(true)
    setError(null)
    try {
      // noRefresh: a 401 here means "wrong password", not an expired session (must not trigger refresh/logout).
      await apiRequest<void>('/me', { method: 'DELETE', body: { password }, noRefresh: true })
      toast.success('Tu cuenta y todos tus datos fueron eliminados')
      await logout()
      navigate('/login', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError && err.code === 'INVALID_CREDENTIALS' ? 'La contraseña no es correcta.' : errorMessage(err))
    } finally {
      setPending(false)
    }
  }

  return (
    <form onSubmit={(e) => void submit(e)} noValidate className="grid gap-3 rounded-xl border-2 border-destructive/40 bg-destructive/5 p-4" aria-label="Eliminar cuenta">
      <h3 className="flex items-center gap-2 font-medium text-destructive">
        <ShieldAlert className="size-4" aria-hidden /> Zona de peligro: eliminar mi cuenta
      </h3>
      <p className="text-sm">
        Esta acción es <strong>irreversible</strong>. Se eliminarán tu perfil, CV y archivos subidos, cartas, postulaciones, búsquedas y
        credenciales o claves guardadas. Te recomendamos exportar tus datos antes.
      </p>
      <div className="grid gap-3 sm:grid-cols-2">
        <FormField id="del-confirm" label={`Escribe ${DELETE_CONFIRMATION} para confirmar`}>
          <Input value={confirm} onChange={(e) => setConfirm(e.target.value)} autoComplete="off" />
        </FormField>
        <FormField id="del-password" label="Tu contraseña" error={error ?? undefined}>
          <Input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </FormField>
      </div>
      <div>
        <Button type="submit" variant="destructive" disabled={!ready || pending}>
          {pending ? <Loader2 className="animate-spin" aria-hidden /> : <Trash2 aria-hidden />}
          Eliminar mi cuenta definitivamente
        </Button>
      </div>
    </form>
  )
}

export function DataRightsCard() {
  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h2 className="text-base font-semibold">Mis datos</h2>
        </CardTitle>
        <CardDescription>Ejerce tu derecho de acceso y de eliminación.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-6">
        <ExportSection />
        <DeleteSection />
      </CardContent>
    </Card>
  )
}
