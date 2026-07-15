import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import DescriptorChips from './DescriptorChips'

describe('DescriptorChips', () => {
  it('renders one chip per descriptor', () => {
    render(<DescriptorChips value={['cotton', 'slim']} onChange={vi.fn()} />)

    expect(screen.getByText('cotton')).toBeInTheDocument()
    expect(screen.getByText('slim')).toBeInTheDocument()
  })

  it('emits the list without a chip when its remove control is tapped', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<DescriptorChips value={['cotton', 'slim']} onChange={onChange} />)

    await user.click(screen.getByRole('button', { name: /remove cotton/i }))

    expect(onChange).toHaveBeenCalledWith(['slim'])
  })

  it('adds a typed descriptor to the list', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<DescriptorChips value={['cotton']} onChange={onChange} />)

    await user.type(screen.getByLabelText(/add a descriptor/i), 'linen')
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    expect(onChange).toHaveBeenCalledWith(['cotton', 'linen'])
  })

  it('ignores a blank add and does not duplicate an existing descriptor', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<DescriptorChips value={['cotton']} onChange={onChange} />)

    // Blank add is a no-op.
    await user.click(screen.getByRole('button', { name: /^add$/i }))
    expect(onChange).not.toHaveBeenCalled()

    // Duplicate add is a no-op.
    await user.type(screen.getByLabelText(/add a descriptor/i), 'cotton')
    await user.click(screen.getByRole('button', { name: /^add$/i }))
    expect(onChange).not.toHaveBeenCalled()
  })
})
