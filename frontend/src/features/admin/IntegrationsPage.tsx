import { useSearchParams } from 'react-router'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { AdminSourcesTab } from './AdminSourcesTab'
import { AiRoutesTab } from './AiRoutesTab'
import { PromptsTab } from './PromptsTab'
import { UsageCard } from './UsageCard'
import { ApifyActorsTab } from './ApifyActorsTab'
import { useIntegrations } from './api'
import { FxRatesTab } from './FxRatesTab'
import { IntegrationCard } from './IntegrationCard'
import type { IntegrationCategory } from './types'

const categories: { id: IntegrationCategory; label: string }[] = [
  { id: 'AI', label: 'IA' },
  { id: 'JOB_SOURCE', label: 'Fuentes de empleo' },
  { id: 'SCRAPING', label: 'Scraping' },
]

function IntegrationsList() {
  const { data, isLoading, error, refetch } = useIntegrations()
  if (isLoading) return <LoadingState />
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />
  return (
    <div className="grid gap-8">
      {categories.map((c) => {
        const items = (data ?? []).filter((i) => i.category === c.id)
        if (!items.length) return null
        return (
          <section key={c.id} aria-labelledby={`cat-${c.id}`} className="grid gap-3">
            <h2 id={`cat-${c.id}`} className="text-lg font-semibold">
              {c.label}
            </h2>
            <div className="grid gap-4 xl:grid-cols-2">
              {items.map((i) => (
                <IntegrationCard key={i.code} integration={i} />
              ))}
            </div>
          </section>
        )
      })}
    </div>
  )
}

const TABS = ['integrations', 'ai-routes', 'prompts', 'sources', 'apify', 'fx'] as const

export function IntegrationsPage() {
  const [params, setParams] = useSearchParams()
  const requested = params.get('tab')
  const tab = TABS.includes(requested as (typeof TABS)[number]) ? requested! : 'integrations'
  return (
    <>
      <PageHeader title="Integraciones" description="Proveedores de IA, fuentes de empleo, límites de uso y credenciales. Solo administradores." />
      <Tabs value={tab} onValueChange={(v) => setParams(v === 'integrations' ? {} : { tab: v }, { replace: true })}>
        <div className="-mx-4 overflow-x-auto px-4 pb-1 md:mx-0 md:px-0">
        <TabsList className="w-max">
          <TabsTrigger value="integrations">Integraciones</TabsTrigger>
          <TabsTrigger value="ai-routes">Rutas de IA</TabsTrigger>
          <TabsTrigger value="prompts">Prompts</TabsTrigger>
          <TabsTrigger value="sources">Fuentes</TabsTrigger>
          <TabsTrigger value="apify">Actors de Apify</TabsTrigger>
          <TabsTrigger value="fx">Tipos de cambio</TabsTrigger>
        </TabsList>
        </div>
        <TabsContent value="integrations" className="grid gap-6 pt-4">
          <UsageCard />
          <IntegrationsList />
        </TabsContent>
        <TabsContent value="ai-routes" className="pt-4">
          <AiRoutesTab />
        </TabsContent>
        <TabsContent value="prompts" className="pt-4">
          <PromptsTab />
        </TabsContent>
        <TabsContent value="sources" className="pt-4">
          <AdminSourcesTab />
        </TabsContent>
        <TabsContent value="apify" className="pt-4">
          <ApifyActorsTab />
        </TabsContent>
        <TabsContent value="fx" className="pt-4">
          <FxRatesTab />
        </TabsContent>
      </Tabs>
    </>
  )
}
