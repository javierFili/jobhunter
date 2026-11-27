import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { evidenceLevelLabels } from '@/lib/labels'
import type { EvidenceLevel, UserSkillDto } from '@/lib/types'

const ORDER: EvidenceLevel[] = ['PROFESSIONAL', 'PROJECT_ONLY', 'ACADEMIC', 'LEARNING']

/** Real data only: count of the user's skills per evidence level. */
export function SkillsEvidenceChart({ skills }: { skills: UserSkillDto[] }) {
  const data = ORDER.map((level) => ({
    level,
    label: evidenceLevelLabels[level],
    count: skills.filter((s) => s.evidenceLevel === level).length,
  }))
  return (
    <figure className="grid gap-2">
      <div className="h-56 w-full" aria-hidden>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 8, right: 8, left: -16, bottom: 0 }} barCategoryGap="30%">
            <CartesianGrid vertical={false} stroke="var(--border)" />
            <XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fill: 'var(--muted-foreground)', fontSize: 12 }} interval={0} />
            <YAxis allowDecimals={false} tickLine={false} axisLine={false} tick={{ fill: 'var(--muted-foreground)', fontSize: 12 }} />
            <Tooltip
              cursor={{ fill: 'var(--muted)' }}
              contentStyle={{
                background: 'var(--popover)',
                color: 'var(--popover-foreground)',
                border: '1px solid var(--border)',
                borderRadius: 8,
                fontSize: 12,
              }}
              formatter={(value) => [value, 'Habilidades']}
            />
            <Bar dataKey="count" fill="var(--chart-1)" radius={[4, 4, 0, 0]} maxBarSize={48} />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <table className="sr-only">
        <caption>Habilidades por nivel de evidencia</caption>
        <thead>
          <tr>
            <th scope="col">Nivel</th>
            <th scope="col">Cantidad</th>
          </tr>
        </thead>
        <tbody>
          {data.map((d) => (
            <tr key={d.level}>
              <td>{d.label}</td>
              <td>{d.count}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  )
}
