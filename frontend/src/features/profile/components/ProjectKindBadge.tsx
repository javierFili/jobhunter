import { StatusBadge } from '@/components/StatusBadge'
import { projectKindLabels } from '@/lib/labels'
import type { ProjectKind } from '@/lib/types'
import { projectKindStyle } from './projectKindStyle'

/** Prominent project-kind badge. Projects are never employment. */
export function ProjectKindBadge({ kind }: { kind: ProjectKind }) {
  const { tone, icon: Icon } = projectKindStyle[kind]
  return (
    <StatusBadge
      tone={tone}
      className="h-6 px-2.5 text-[0.8rem] font-semibold"
      icon={<Icon aria-hidden />}
      title="Tipo de proyecto (no es experiencia laboral)"
    >
      <span data-testid="project-kind">{projectKindLabels[kind]}</span>
    </StatusBadge>
  )
}
