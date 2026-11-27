import { ShieldCheck, TriangleAlert } from 'lucide-react'
import { StatusBadge } from '@/components/StatusBadge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { TRUTH_PRINCIPLE, violationRuleLabels, type TruthReport } from '@/lib/truth'

/** "Verificación de veracidad": grounded/suggested counts and rule violations found by the backend validator. */
export function ValidationReportPanel({ report, showPrinciple = true }: { report: TruthReport; showPrinciple?: boolean }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h3 className="flex items-center gap-2 text-base font-semibold">
            <ShieldCheck className="size-4 text-emerald-600" aria-hidden /> Verificación de veracidad
          </h3>
        </CardTitle>
        {showPrinciple && <CardDescription>{TRUTH_PRINCIPLE}</CardDescription>}
      </CardHeader>
      <CardContent className="grid gap-3">
        <div className="flex flex-wrap gap-2">
          <StatusBadge tone="success">{report.groundedCount} con fuente verificada</StatusBadge>
          <StatusBadge tone="warning">{report.suggestedCount} sugerencias por revisar</StatusBadge>
          <StatusBadge tone={report.violations.length ? 'danger' : 'neutral'}>{report.violations.length} observaciones</StatusBadge>
          {report.provider && (
            <StatusBadge tone="violet">
              {report.provider} {report.model ?? ''}
            </StatusBadge>
          )}
        </div>
        {report.violations.length > 0 && (
          <ul className="grid gap-2" aria-label="Observaciones de veracidad">
            {report.violations.map((v, i) => (
              <li key={i} className="grid gap-0.5 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-950 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-100">
                <span className="flex items-center gap-1.5 font-medium">
                  <TriangleAlert className="size-4 shrink-0" aria-hidden />
                  {violationRuleLabels[v.rule] ?? v.rule} · {v.section}
                </span>
                <span>{v.message}</span>
                {v.text && <span className="text-xs italic">«{v.text}»</span>}
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
