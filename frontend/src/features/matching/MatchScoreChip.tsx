import { Gauge } from 'lucide-react'
import { StatusBadge } from '@/components/StatusBadge'
import { classificationLabels, classificationTone } from './labels'
import type { Classification } from './types'

export function MatchScoreChip({ score, classification }: { score: number; classification: Classification }) {
  return (
    <StatusBadge tone={classificationTone[classification]} icon={<Gauge aria-hidden />} title={classificationLabels[classification]}>
      <span>
        <span className="sr-only">Compatibilidad: </span>
        {Math.round(score)}
        <span className="sr-only"> de 100, {classificationLabels[classification]}</span>
      </span>
    </StatusBadge>
  )
}
