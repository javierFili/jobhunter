import { ChipMultiSelect } from '@/components/ChipMultiSelect'
import { FormField } from '@/components/FormField'
import { SearchableMultiSelect } from '@/components/SearchableMultiSelect'
import { SelectField } from '@/components/SelectField'
import { Input } from '@/components/ui/input'
import { SkillMultiPicker } from '@/features/profile/components/SkillMultiPicker'
import { useUserSkills } from '@/features/profile/api'
import { COUNTRY_CODES, LANGUAGE_CODES, countryName, languageName, regionLabels, seniorityLabels, toOptions } from '@/lib/labels'
import type { Region, Seniority } from '@/lib/types'
import { POSTED_WITHIN_OPTIONS, eligibilityLabels, jobContractLabels, jobEngagementLabels, jobModalityLabels, remoteScopeLabels } from './labels'
import type { Eligibility, JobContractType, JobEngagementType, JobModality, RemoteScope, SearchFilters } from './types'

const splitList = (text: string) => text.split(',').map((x) => x.trim()).filter(Boolean)

const countryOptions = COUNTRY_CODES.map((c) => ({ value: c as string, label: `${countryName(c)} (${c})` }))
const languageOptions = LANGUAGE_CODES.map((c) => ({ value: c as string, label: languageName(c) }))

interface Props {
  idPrefix: string
  value: SearchFilters
  onChange: (value: SearchFilters) => void
  /** Show the company include/exclude lists (saved searches). */
  withCompanies?: boolean
}

/** Filter controls shared by the jobs page and the saved-search dialog. */
export function FiltersFields({ idPrefix, value, onChange, withCompanies = false }: Props) {
  const set = (patch: Partial<SearchFilters>) => onChange({ ...value, ...patch })
  const userSkills = useUserSkills()
  const skillName = (id: number) => userSkills.data?.find((s) => s.skillId === id)?.name ?? `Habilidad #${id}`
  const p = (s: string) => `${idPrefix}-${s}`

  return (
    <div className="grid gap-5">
      <FormField id={p('eligibility')} label="Accesibilidad desde Bolivia">
        <ChipMultiSelect<Eligibility> label="Accesibilidad desde Bolivia" options={toOptions(eligibilityLabels)} value={value.eligibility} onChange={(eligibility) => set({ eligibility })} />
      </FormField>
      <FormField id={p('modalities')} label="Modalidad">
        <ChipMultiSelect<JobModality> label="Modalidad" options={toOptions(jobModalityLabels)} value={value.modalities} onChange={(modalities) => set({ modalities })} />
      </FormField>
      <FormField id={p('scopes')} label="Alcance del remoto">
        <ChipMultiSelect<RemoteScope> label="Alcance del remoto" options={toOptions(remoteScopeLabels)} value={value.remoteScopes} onChange={(remoteScopes) => set({ remoteScopes })} />
      </FormField>
      <FormField id={p('regions')} label="Regiones">
        <ChipMultiSelect<Region> label="Regiones" options={toOptions(regionLabels)} value={value.regions} onChange={(regions) => set({ regions })} />
      </FormField>
      <div className="grid gap-5 md:grid-cols-2">
        <FormField id={p('countries')} label="Países">
          <SearchableMultiSelect label="Países" options={countryOptions} value={value.countries} onChange={(countries) => set({ countries })} />
        </FormField>
        <FormField id={p('languages')} label="Idiomas de la oferta">
          <SearchableMultiSelect label="Idiomas de la oferta" options={languageOptions} value={value.languages} onChange={(languages) => set({ languages })} />
        </FormField>
      </div>
      <FormField id={p('contract')} label="Tipo de contrato">
        <ChipMultiSelect<JobContractType> label="Tipo de contrato" options={toOptions(jobContractLabels)} value={value.contractTypes} onChange={(contractTypes) => set({ contractTypes })} />
      </FormField>
      <FormField id={p('engagement')} label="Forma de contratación">
        <ChipMultiSelect<JobEngagementType> label="Forma de contratación" options={toOptions(jobEngagementLabels)} value={value.engagementTypes} onChange={(engagementTypes) => set({ engagementTypes })} />
      </FormField>
      <FormField id={p('seniority')} label="Seniority">
        <ChipMultiSelect<Seniority> label="Seniority" options={toOptions(seniorityLabels)} value={value.seniority} onChange={(seniority) => set({ seniority })} />
      </FormField>
      <div className="grid gap-5 md:grid-cols-2">
        <FormField id={p('salary')} label="Salario mínimo (USD/mes)" hint="Se compara con el salario convertido a USD mensuales.">
          <Input
            inputMode="numeric"
            value={value.salaryMinUsdMonth ?? ''}
            onChange={(e) => {
              const v = e.target.value.replace(/[^\d]/g, '')
              set({ salaryMinUsdMonth: v ? Number(v) : null })
            }}
          />
        </FormField>
        <FormField id={p('posted')} label="Publicadas en">
          <SelectField
            value={value.postedWithinDays != null ? String(value.postedWithinDays) : ''}
            onChange={(v) => set({ postedWithinDays: v ? Number(v) : null })}
            options={POSTED_WITHIN_OPTIONS}
            noneLabel="Cualquier fecha"
          />
        </FormField>
      </div>
      <FormField id={p('skills')} label="Habilidades">
        <SkillMultiPicker
          value={value.skillIds.map((id) => ({ id, name: skillName(id) }))}
          onChange={(skills) => set({ skillIds: skills.map((s) => s.id) })}
        />
      </FormField>
      {withCompanies && (
        <div className="grid gap-5 md:grid-cols-2">
          <FormField id={p('companies')} label="Solo estas empresas" hint="Separadas por coma.">
            <Input defaultValue={value.companies.join(', ')} onBlur={(e) => set({ companies: splitList(e.target.value) })} />
          </FormField>
          <FormField id={p('exclude-companies')} label="Excluir empresas" hint="Separadas por coma.">
            <Input defaultValue={value.excludeCompanies.join(', ')} onBlur={(e) => set({ excludeCompanies: splitList(e.target.value) })} />
          </FormField>
        </div>
      )}
    </div>
  )
}
