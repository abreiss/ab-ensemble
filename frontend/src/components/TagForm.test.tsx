import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import TagForm from './TagForm'
import { CATEGORIES } from '../lib/categoryTaxonomy'
import type { TagSuggestion } from '../types/item'

const fullSuggestion: TagSuggestion = {
  category: 'shirt',
  primaryColor: 'navy',
  secondaryColor: 'white',
  formality: 3,
  pattern: 'striped',
  warmth: 2,
  descriptors: ['cotton'],
}

const allNull: TagSuggestion = {
  category: null,
  primaryColor: null,
  secondaryColor: null,
  formality: null,
  pattern: null,
  warmth: null,
  descriptors: null,
}

describe('TagForm', () => {
  it('pre-fills every field from a suggestion', () => {
    render(<TagForm initial={fullSuggestion} submitLabel="Save" onSubmit={vi.fn()} />)

    // The category control is a taxonomy `<select>` (Task 2.0): a suggestion's
    // raw category is normalized to its bucket ("shirt" → "Top"), not shown
    // verbatim — see the dedicated normalization tests below.
    expect(screen.getByLabelText(/^category/i)).toHaveValue('Top')
    expect(screen.getByLabelText(/primary color/i)).toHaveValue('navy')
    expect(screen.getByLabelText(/secondary color/i)).toHaveValue('white')
    expect(screen.getByLabelText(/formality/i)).toHaveValue('3')
    expect(screen.getByLabelText(/pattern/i)).toHaveValue('striped')
    expect(screen.getByLabelText(/warmth/i)).toHaveValue('2')
    expect(screen.getByText('cotton')).toBeInTheDocument()
  })

  it('renders a null suggestion as empty, editable fields (degraded vision is normal)', () => {
    render(<TagForm initial={allNull} submitLabel="Save" onSubmit={vi.fn()} />)

    expect(screen.getByLabelText(/^category/i)).toHaveValue('')
    expect(screen.getByLabelText(/primary color/i)).toHaveValue('')
    expect(screen.getByLabelText(/formality/i)).toHaveValue('')
    // A null suggestion must be editable, not an error state.
    expect(screen.getByLabelText(/^category/i)).toBeEnabled()
  })

  it('disables save until the required fields are valid, then submits the edited tags', async () => {
    const onSubmit = vi.fn()
    const user = userEvent.setup()
    render(<TagForm initial={allNull} submitLabel="Save" onSubmit={onSubmit} />)

    const save = screen.getByRole('button', { name: /save/i })
    expect(save).toBeDisabled()

    await user.selectOptions(screen.getByLabelText(/^category/i), 'Jacket')
    await user.selectOptions(screen.getByLabelText(/formality/i), '4')
    await user.selectOptions(screen.getByLabelText(/warmth/i), '2')

    expect(save).toBeEnabled()
    await user.click(save)

    expect(onSubmit).toHaveBeenCalledTimes(1)
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ category: 'Jacket', formality: 4, warmth: 2 }),
    )
  })

  it('omits blank optional fields from the submitted payload', async () => {
    const onSubmit = vi.fn()
    const user = userEvent.setup()
    render(<TagForm initial={allNull} submitLabel="Save" onSubmit={onSubmit} />)

    await user.selectOptions(screen.getByLabelText(/^category/i), 'Top')
    await user.selectOptions(screen.getByLabelText(/formality/i), '1')
    await user.selectOptions(screen.getByLabelText(/warmth/i), '1')
    await user.click(screen.getByRole('button', { name: /save/i }))

    const payload = onSubmit.mock.calls[0][0]
    expect(payload.category).toBe('Top')
    expect(payload.primaryColor ?? null).toBeNull()
    expect(payload.pattern ?? null).toBeNull()
  })

  it('keeps save disabled while a submit is in flight', () => {
    render(
      <TagForm initial={fullSuggestion} submitLabel="Save" onSubmit={vi.fn()} submitting />,
    )
    expect(screen.getByRole('button', { name: /sav/i })).toBeDisabled()
  })

  it('de-duplicates repeated descriptors from a suggestion so a chip is not rendered twice', () => {
    // Vision (and the backend) can return the same descriptor more than once;
    // seeding duplicates would collide React keys and let one remove wipe both.
    render(
      <TagForm
        initial={{ ...allNull, descriptors: ['blue', 'blue', 'soft'] }}
        submitLabel="Save"
        onSubmit={vi.fn()}
      />,
    )

    expect(screen.getAllByText('blue')).toHaveLength(1)
    expect(screen.getByText('soft')).toBeInTheDocument()
  })

  describe('category taxonomy select', () => {
    it('renders the category control as a <select> listing exactly the taxonomy values, with a — placeholder', () => {
      render(<TagForm initial={allNull} submitLabel="Save" onSubmit={vi.fn()} />)

      const select = screen.getByLabelText(/^category/i) as HTMLSelectElement
      expect(select.tagName).toBe('SELECT')
      const optionValues = Array.from(select.options).map((option) => option.value)
      expect(optionValues).toEqual(['', ...CATEGORIES])
    })

    it('lets Jewelry save with warmth/formality left unset — both are optional', async () => {
      const onSubmit = vi.fn()
      const onChange = vi.fn()
      const user = userEvent.setup()
      render(
        <TagForm initial={allNull} submitLabel="Save" onSubmit={onSubmit} onChange={onChange} />,
      )

      const save = screen.getByRole('button', { name: /save/i })
      expect(save).toBeDisabled()

      await user.selectOptions(screen.getByLabelText(/^category/i), 'Jewelry')

      // Selecting only the category (no formality/warmth) already makes the
      // form submittable — the null warmth/formality below prove the relaxed
      // validation, not a lucky default.
      expect(save).toBeEnabled()
      expect(onChange).toHaveBeenCalledWith(
        expect.objectContaining({ category: 'Jewelry', formality: null, warmth: null }),
      )

      await user.click(save)

      expect(onSubmit).toHaveBeenCalledWith(
        expect.objectContaining({ category: 'Jewelry', formality: null, warmth: null }),
      )
    })

    it('pre-selects the normalized bucket for a legacy stored category (edit-time normalization)', () => {
      render(
        <TagForm
          initial={{ ...allNull, category: 'chinos' }}
          submitLabel="Save"
          onSubmit={vi.fn()}
        />,
      )

      // A legacy/off-taxonomy stored value never renders blank or off-list —
      // it pre-selects its normalized bucket ("chinos" → "Bottom").
      expect(screen.getByLabelText(/^category/i)).toHaveValue('Bottom')
    })
  })
})
