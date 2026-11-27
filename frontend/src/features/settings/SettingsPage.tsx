import { useEffect } from 'react'
import { useLocation } from 'react-router'
import { PageHeader } from '@/components/PageHeader'
import { AccountCard } from './AccountCard'
import { ConsentsCard } from './ConsentsCard'
import { DataRightsCard } from './DataRightsCard'
import { PasswordCard } from './PasswordCard'

export function SettingsPage() {
  const { hash } = useLocation()
  useEffect(() => {
    if (!hash) return
    const t = setTimeout(() => document.getElementById(decodeURIComponent(hash.slice(1)))?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 300)
    return () => clearTimeout(t)
  }, [hash])
  return (
    <>
      <PageHeader title="Configuración" description="Administra tu cuenta, seguridad y privacidad." />
      <div className="grid gap-4">
        <AccountCard />
        <PasswordCard />
        <ConsentsCard />
        <DataRightsCard />
      </div>
    </>
  )
}
