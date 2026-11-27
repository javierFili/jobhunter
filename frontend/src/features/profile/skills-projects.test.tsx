import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { employmentTypeLabels, evidenceLevelLabels } from '@/lib/labels'
import { renderApp } from '@/test/render'

describe('Habilidades — evidence levels', () => {
  it('has the exact Spanish labels for each evidence level', () => {
    expect(evidenceLevelLabels).toEqual({
      PROFESSIONAL: 'Experiencia profesional',
      PROJECT_ONLY: 'Solo proyectos',
      ACADEMIC: 'Académico',
      LEARNING: 'Aprendiendo',
    })
  })

  it('shows evidence-level labels and computed professional years in the table', async () => {
    renderApp('/profile?tab=skills')
    const table = await screen.findByRole('table', { name: 'Mis habilidades' })
    const row = (name: string) => within(table).getByText(name).closest('tr')!
    expect(within(row('TEST DATA Java')).getByText('Experiencia profesional')).toBeInTheDocument()
    expect(within(row('TEST DATA Java')).getByText('3,5 años')).toBeInTheDocument()
    expect(within(row('TEST DATA React')).getByText('Solo proyectos')).toBeInTheDocument()
    expect(within(row('TEST DATA React')).getByText('0 años')).toBeInTheDocument()
    expect(within(row('TEST DATA PostgreSQL')).getByText('Académico')).toBeInTheDocument()
    expect(within(row('TEST DATA Docker')).getByText('Aprendiendo')).toBeInTheDocument()
    // no raw enum values leak to the UI
    expect(within(table).queryByText('PROJECT_ONLY')).not.toBeInTheDocument()
  })
})

describe('Proyectos', () => {
  it('shows a visible kind badge for every project and never labels it as employment', async () => {
    renderApp('/profile?tab=projects')
    const expected: Record<string, string> = {
      'Proyecto TEST DATA — Proyecto Personal': 'Personal',
      'Proyecto TEST DATA — Proyecto Académico': 'Académico',
      'Proyecto TEST DATA — Proyecto Cliente': 'Cliente freelance',
      'Proyecto TEST DATA — Proyecto OSS': 'Open source',
    }
    for (const [name, kind] of Object.entries(expected)) {
      const card = await screen.findByRole('article', { name })
      expect(within(card).getByTestId('project-kind')).toHaveTextContent(kind)
      const text = card.textContent ?? ''
      for (const employmentLabel of Object.values(employmentTypeLabels)) {
        expect(text).not.toContain(employmentLabel)
      }
      expect(text).not.toMatch(/empleo|experiencia laboral/i)
    }
    expect(screen.getByText(/no cuentan como experiencia laboral/)).toBeInTheDocument()
  })
})
