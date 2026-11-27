import { StatusBadge, type BadgeTone } from '@/components/StatusBadge'
import { evidenceLevelLabels } from '@/lib/labels'
import type { EvidenceLevel } from '@/lib/types'

const tones: Record<EvidenceLevel, BadgeTone> = {
  PROFESSIONAL: 'success',
  PROJECT_ONLY: 'violet',
  ACADEMIC: 'info',
  LEARNING: 'neutral',
}

export function EvidenceBadge({ level }: { level: EvidenceLevel }) {
  return <StatusBadge tone={tones[level]}>{evidenceLevelLabels[level]}</StatusBadge>
}
