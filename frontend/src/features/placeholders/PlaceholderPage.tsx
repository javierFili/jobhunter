import { Clock } from 'lucide-react'
import { PageHeader } from '@/components/PageHeader'
import { EmptyState } from '@/components/EmptyState'
import type { NavItem } from '@/app/layout/nav'

const descriptions: Record<string, string> = {
  '/jobs': 'Aquí podrás buscar ofertas en múltiples portales y filtrarlas según tus preferencias.',
  '/searches': 'Guarda combinaciones de filtros y recibe nuevas ofertas automáticamente.',
  '/recommendations': 'Ofertas ordenadas por afinidad con tu perfil, con explicación de cada puntaje.',
  '/cvs': 'Sube tu CV para importarlo y genera versiones adaptadas a cada oferta.',
  '/applications': 'Haz seguimiento de cada postulación: estados, fechas y próximos pasos.',
  '/cover-letters': 'Genera y edita cartas de presentación a partir de tu perfil verificado.',
  '/analytics': 'Métricas de tu búsqueda: tasa de respuesta, entrevistas y tendencias.',
  '/admin/integrations': 'Configura los portales de empleo y proveedores conectados.',
}

export function PlaceholderPage({ item }: { item: NavItem }) {
  return (
    <>
      <PageHeader title={item.label} />
      <EmptyState
        icon={Clock}
        title={`Disponible en la Fase ${item.phase}`}
        description={descriptions[item.to] ?? 'Esta sección estará disponible próximamente.'}
        className="py-16"
      />
    </>
  )
}
