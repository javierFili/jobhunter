import type { ReactNode } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Loader2, Save } from 'lucide-react'
import { toast } from 'sonner'
import { ChipMultiSelect } from '@/components/ChipMultiSelect'
import { ErrorState } from '@/components/ErrorState'
import { FormField } from '@/components/FormField'
import { LoadingState } from '@/components/LoadingState'
import { SearchableMultiSelect } from '@/components/SearchableMultiSelect'
import { SelectField } from '@/components/SelectField'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import {
  COUNTRY_CODES,
  CURRENCIES,
  LANGUAGE_CODES,
  contractTypeLabels,
  countryName,
  engagementTypeLabels,
  languageName,
  regionLabels,
  salaryPeriodLabels,
  seniorityLabels,
  toOptions,
  workModalityLabels,
} from '@/lib/labels'
import type { ContractType, EngagementType, PreferencesDto, Region, SalaryPeriod, Seniority, WorkModality } from '@/lib/types'
import { handleFormError } from '@/lib/zod'
import { usePreferences, useSavePreferences } from '../api'

const schema = z
  .object({
    regions: z.array(z.string()),
    countriesInclude: z.array(z.string()),
    countriesExclude: z.array(z.string()),
    modalities: z.array(z.string()),
    contractTypes: z.array(z.string()),
    engagementTypes: z.array(z.string()),
    minSalary: z.string().refine((v) => v === '' || (/^\d+(\.\d{1,2})?$/.test(v) && Number(v) >= 0), 'Ingresa un monto válido'),
    salaryCurrency: z.string(),
    salaryPeriod: z.string(),
    workLanguages: z.array(z.string()),
    seniority: z.string(),
    relocationOk: z.boolean(),
  })
  .superRefine((v, ctx) => {
    if (v.minSalary !== '') {
      if (!v.salaryCurrency) ctx.addIssue({ code: 'custom', path: ['salaryCurrency'], message: 'Elige la moneda' })
      if (!v.salaryPeriod) ctx.addIssue({ code: 'custom', path: ['salaryPeriod'], message: 'Elige el periodo' })
    }
    const both = v.countriesInclude.filter((c) => v.countriesExclude.includes(c))
    if (both.length) {
      ctx.addIssue({ code: 'custom', path: ['countriesExclude'], message: 'Un país no puede estar incluido y excluido a la vez' })
    }
  })

type Values = z.infer<typeof schema>

const countryOptions = COUNTRY_CODES.map((c) => ({ value: c as string, label: `${countryName(c)} (${c})` }))
const workLanguageOptions = LANGUAGE_CODES.map((c) => ({ value: c as string, label: languageName(c) }))
const currencyOptions = CURRENCIES.map((c) => ({ value: c as string, label: c }))

const toValues = (p: PreferencesDto): Values => ({
  regions: p.regions,
  countriesInclude: p.countriesInclude,
  countriesExclude: p.countriesExclude,
  modalities: p.modalities,
  contractTypes: p.contractTypes,
  engagementTypes: p.engagementTypes,
  minSalary: p.minSalary == null ? '' : String(p.minSalary),
  salaryCurrency: p.salaryCurrency ?? '',
  salaryPeriod: p.salaryPeriod ?? '',
  workLanguages: p.workLanguages,
  seniority: p.seniority ?? '',
  relocationOk: p.relocationOk,
})

function Section({ title, description, children }: { title: string; description?: string; children: ReactNode }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h3 className="text-base font-semibold">{title}</h3>
        </CardTitle>
        {description && <CardDescription>{description}</CardDescription>}
      </CardHeader>
      <CardContent className="grid gap-5">{children}</CardContent>
    </Card>
  )
}

function PreferencesForm({ initial }: { initial: PreferencesDto }) {
  const save = useSavePreferences()
  const {
    control,
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isDirty },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: toValues(initial) })
  const included = useWatch({ control, name: 'countriesInclude' })
  const excluded = useWatch({ control, name: 'countriesExclude' })

  const onSubmit = handleSubmit(async (v) => {
    const body: PreferencesDto = {
      regions: v.regions as Region[],
      countriesInclude: v.countriesInclude,
      countriesExclude: v.countriesExclude,
      modalities: v.modalities as WorkModality[],
      contractTypes: v.contractTypes as ContractType[],
      engagementTypes: v.engagementTypes as EngagementType[],
      minSalary: v.minSalary === '' ? null : Number(v.minSalary),
      salaryCurrency: v.salaryCurrency || null,
      salaryPeriod: (v.salaryPeriod || null) as SalaryPeriod | null,
      workLanguages: v.workLanguages,
      seniority: (v.seniority || null) as Seniority | null,
      relocationOk: v.relocationOk,
    }
    try {
      const saved = await save.mutateAsync(body)
      reset(toValues({ ...body, ...saved }))
      toast.success('Preferencias guardadas')
    } catch (e) {
      handleFormError(e, setError)
    }
  })

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-4">
      <Section title="Ubicación" description="Dónde quieres trabajar (o desde dónde te pueden contratar).">
        <FormField id="pref-regions" label="Regiones" error={errors.regions?.message}>
          <Controller
            control={control}
            name="regions"
            render={({ field }) => (
              <ChipMultiSelect label="Regiones" options={toOptions(regionLabels)} value={field.value} onChange={field.onChange} />
            )}
          />
        </FormField>
        <div className="grid gap-5 md:grid-cols-2">
          <FormField
            id="pref-countries-include"
            label="Países a incluir"
            hint="Vacío = todos los de las regiones elegidas."
            error={errors.countriesInclude?.message}
          >
            <Controller
              control={control}
              name="countriesInclude"
              render={({ field }) => (
                <SearchableMultiSelect
                  label="Países a incluir"
                  options={countryOptions}
                  value={field.value}
                  onChange={field.onChange}
                  disabledValues={excluded}
                />
              )}
            />
          </FormField>
          <FormField id="pref-countries-exclude" label="Países a excluir" error={errors.countriesExclude?.message}>
            <Controller
              control={control}
              name="countriesExclude"
              render={({ field }) => (
                <SearchableMultiSelect
                  label="Países a excluir"
                  options={countryOptions}
                  value={field.value}
                  onChange={field.onChange}
                  disabledValues={included}
                />
              )}
            />
          </FormField>
        </div>
        <div className="flex items-center gap-3">
          <Controller
            control={control}
            name="relocationOk"
            render={({ field }) => <Switch id="pref-relocation" checked={field.value} onCheckedChange={field.onChange} />}
          />
          <Label htmlFor="pref-relocation" className="font-normal">
            Estoy dispuesto a reubicarme
          </Label>
        </div>
      </Section>

      <Section title="Tipo de trabajo">
        <FormField id="pref-modalities" label="Modalidad">
          <Controller
            control={control}
            name="modalities"
            render={({ field }) => (
              <ChipMultiSelect label="Modalidad" options={toOptions(workModalityLabels)} value={field.value} onChange={field.onChange} />
            )}
          />
        </FormField>
        <FormField id="pref-contract" label="Tipo de contrato">
          <Controller
            control={control}
            name="contractTypes"
            render={({ field }) => (
              <ChipMultiSelect label="Tipo de contrato" options={toOptions(contractTypeLabels)} value={field.value} onChange={field.onChange} />
            )}
          />
        </FormField>
        <FormField
          id="pref-engagement"
          label="Forma de contratación"
          hint="EOR: empresa empleadora que te contrata localmente en nombre de un empleador extranjero."
        >
          <Controller
            control={control}
            name="engagementTypes"
            render={({ field }) => (
              <ChipMultiSelect label="Forma de contratación" options={toOptions(engagementTypeLabels)} value={field.value} onChange={field.onChange} />
            )}
          />
        </FormField>
        <div className="grid gap-5 md:grid-cols-2">
          <FormField id="pref-seniority" label="Seniority" error={errors.seniority?.message}>
            <Controller
              control={control}
              name="seniority"
              render={({ field }) => (
                <SelectField value={field.value} onChange={field.onChange} options={toOptions(seniorityLabels)} noneLabel="Sin especificar" />
              )}
            />
          </FormField>
          <FormField id="pref-work-languages" label="Idiomas de trabajo">
            <Controller
              control={control}
              name="workLanguages"
              render={({ field }) => (
                <SearchableMultiSelect label="Idiomas de trabajo" options={workLanguageOptions} value={field.value} onChange={field.onChange} />
              )}
            />
          </FormField>
        </div>
      </Section>

      <Section title="Salario" description="Salario mínimo aceptable. Se usa para filtrar ofertas, nunca se comparte.">
        <div className="grid gap-5 md:grid-cols-3">
          <FormField id="pref-min-salary" label="Salario mínimo" error={errors.minSalary?.message}>
            <Input inputMode="decimal" placeholder="Ej.: 2500" {...register('minSalary')} />
          </FormField>
          <FormField id="pref-currency" label="Moneda" error={errors.salaryCurrency?.message}>
            <Controller
              control={control}
              name="salaryCurrency"
              render={({ field }) => (
                <SelectField value={field.value} onChange={field.onChange} options={currencyOptions} noneLabel="Sin especificar" />
              )}
            />
          </FormField>
          <FormField id="pref-period" label="Periodo" error={errors.salaryPeriod?.message}>
            <Controller
              control={control}
              name="salaryPeriod"
              render={({ field }) => (
                <SelectField value={field.value} onChange={field.onChange} options={toOptions(salaryPeriodLabels)} noneLabel="Sin especificar" />
              )}
            />
          </FormField>
        </div>
      </Section>

      <div className="sticky bottom-0 -mx-1 flex justify-end gap-2 bg-background/90 px-1 py-3 backdrop-blur">
        <Button type="button" variant="outline" disabled={!isDirty || save.isPending} onClick={() => reset()}>
          Descartar cambios
        </Button>
        <Button type="submit" disabled={save.isPending}>
          {save.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <Save aria-hidden />}
          Guardar preferencias
        </Button>
      </div>
    </form>
  )
}

export function PreferencesTab() {
  const { data, isLoading, error, refetch, dataUpdatedAt } = usePreferences()
  return (
    <section aria-labelledby="tab-pref-title">
      <h2 id="tab-pref-title" className="sr-only">
        Preferencias laborales
      </h2>
      {isLoading ? (
        <LoadingState rows={3} />
      ) : error || !data ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <PreferencesForm key={dataUpdatedAt} initial={data} />
      )}
    </section>
  )
}
