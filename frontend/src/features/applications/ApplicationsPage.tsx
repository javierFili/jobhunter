import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { Plus } from 'lucide-react'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ApplicationsTable } from './ApplicationsTable'
import { KanbanBoard } from './KanbanBoard'
import { NEVER_SENDS } from './labels'
import { NewApplicationDialog } from './NewApplicationDialog'

export function ApplicationsPage() {
  const [params, setParams] = useSearchParams()
  const [creating, setCreating] = useState(false)
  const tab = params.get('view') === 'table' ? 'table' : 'kanban'
  return (
    <>
      <PageHeader
        title="Postulaciones"
        description={NEVER_SENDS}
        actions={
          <Button onClick={() => setCreating(true)}>
            <Plus aria-hidden /> Nueva postulación manual
          </Button>
        }
      />
      <Tabs value={tab} onValueChange={(v) => setParams(v === 'table' ? { view: v } : {}, { replace: true })}>
        <TabsList>
          <TabsTrigger value="kanban">Kanban</TabsTrigger>
          <TabsTrigger value="table">Tabla</TabsTrigger>
        </TabsList>
        <TabsContent value="kanban" className="pt-4">
          <KanbanBoard />
        </TabsContent>
        <TabsContent value="table" className="pt-4">
          <ApplicationsTable />
        </TabsContent>
      </Tabs>
      <NewApplicationDialog open={creating} onOpenChange={setCreating} />
    </>
  )
}
