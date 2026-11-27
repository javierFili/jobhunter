import type { ReactNode } from 'react'
import { Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { formatDate } from '@/lib/format'

const tooltipStyle = {
  background: 'var(--popover)',
  color: 'var(--popover-foreground)',
  border: '1px solid var(--border)',
  borderRadius: 8,
  fontSize: 12,
}
const tick = { fill: 'var(--muted-foreground)', fontSize: 12 }

/** Accessible data table mirrored from each chart (screen readers / no-color reading). */
export function SrTable({ caption, headers, rows }: { caption: string; headers: string[]; rows: (string | number)[][] }) {
  return (
    <table className="sr-only">
      <caption>{caption}</caption>
      <thead>
        <tr>
          {headers.map((h) => (
            <th key={h} scope="col">
              {h}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((r, i) => (
          <tr key={i}>
            {r.map((c, j) => (
              <td key={j}>{c}</td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function Frame({ height = 240, children }: { height?: number; children: ReactNode }) {
  return (
    <div className="w-full" style={{ height }} aria-hidden>
      <ResponsiveContainer width="100%" height="100%">
        {children as React.ReactElement}
      </ResponsiveContainer>
    </div>
  )
}

const shortDate = (d: string) => formatDate(d).replace(/ de /g, ' ')

export function TimelineChart({ data, height }: { data: { date: string; newJobs: number; eligibleOrLikely: number }[]; height?: number }) {
  return (
    <figure className="grid gap-2">
      <Frame height={height}>
        <LineChart data={data} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
          <CartesianGrid vertical={false} stroke="var(--border)" />
          <XAxis dataKey="date" tickFormatter={shortDate} tickLine={false} axisLine={false} tick={tick} minTickGap={24} />
          <YAxis allowDecimals={false} tickLine={false} axisLine={false} tick={tick} />
          <Tooltip contentStyle={tooltipStyle} labelFormatter={(l) => formatDate(String(l))} />
          <Legend wrapperStyle={{ fontSize: 12 }} />
          <Line type="monotone" dataKey="newJobs" name="Ofertas nuevas" stroke="var(--chart-1)" strokeWidth={2} dot={false} />
          <Line type="monotone" dataKey="eligibleOrLikely" name="Accesibles o probables" stroke="var(--chart-2)" strokeWidth={2} dot={false} />
        </LineChart>
      </Frame>
      <SrTable caption="Ofertas nuevas por día" headers={['Fecha', 'Ofertas nuevas', 'Accesibles o probables']} rows={data.map((d) => [d.date, d.newJobs, d.eligibleOrLikely])} />
    </figure>
  )
}

/** Single-series horizontal bars (one hue). */
export function HBarChart({ data, caption, valueLabel, height }: { data: { label: string; value: number }[]; caption: string; valueLabel: string; height?: number }) {
  const h = height ?? Math.max(120, data.length * 28 + 24)
  return (
    <figure className="grid gap-2">
      <Frame height={h}>
        <BarChart data={data} layout="vertical" margin={{ top: 4, right: 16, left: 8, bottom: 0 }}>
          <CartesianGrid horizontal={false} stroke="var(--border)" />
          <XAxis type="number" allowDecimals={false} tickLine={false} axisLine={false} tick={tick} />
          <YAxis type="category" dataKey="label" width={140} tickLine={false} axisLine={false} tick={tick} />
          <Tooltip contentStyle={tooltipStyle} cursor={{ fill: 'var(--muted)' }} formatter={(v) => [v, valueLabel]} />
          <Bar dataKey="value" name={valueLabel} fill="var(--chart-1)" radius={[0, 4, 4, 0]} maxBarSize={20} />
        </BarChart>
      </Frame>
      <SrTable caption={caption} headers={['Categoría', valueLabel]} rows={data.map((d) => [d.label, d.value])} />
    </figure>
  )
}

/** Stacked bars per day; `series` already folded to ≤ 5 entries (last = "Otros"). */
export function StackedDailyBars({
  data,
  series,
  caption,
}: {
  data: Record<string, string | number>[]
  series: { key: string; label: string; color: string }[]
  caption: string
}) {
  return (
    <figure className="grid gap-2">
      <Frame height={260}>
        <BarChart data={data} margin={{ top: 8, right: 8, left: -8, bottom: 0 }}>
          <CartesianGrid vertical={false} stroke="var(--border)" />
          <XAxis dataKey="date" tickFormatter={shortDate} tickLine={false} axisLine={false} tick={tick} minTickGap={24} />
          <YAxis tickLine={false} axisLine={false} tick={tick} tickFormatter={(v) => `$${v}`} />
          <Tooltip contentStyle={tooltipStyle} cursor={{ fill: 'var(--muted)' }} labelFormatter={(l) => formatDate(String(l))} formatter={(v) => `US$${Number(v).toFixed(4)}`} />
          <Legend wrapperStyle={{ fontSize: 12 }} />
          {series.map((s, i) => (
            <Bar key={s.key} dataKey={s.key} name={s.label} stackId="cost" fill={s.color} stroke="var(--card)" strokeWidth={1} radius={i === series.length - 1 ? [4, 4, 0, 0] : 0} />
          ))}
        </BarChart>
      </Frame>
      <SrTable caption={caption} headers={['Fecha', ...series.map((s) => s.label)]} rows={data.map((d) => [String(d.date), ...series.map((s) => Number(d[s.key] ?? 0).toFixed(4))])} />
    </figure>
  )
}
