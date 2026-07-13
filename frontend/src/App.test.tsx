import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import App from './App'
import { fetchHealth } from './api/health'

vi.mock('./api/health')

const mockedFetchHealth = vi.mocked(fetchHealth)

describe('App', () => {
  beforeEach(() => {
    mockedFetchHealth.mockReset()
  })

  it('renders the backend status when the health call succeeds', async () => {
    mockedFetchHealth.mockResolvedValue({ status: 'ok' })

    render(<App />)

    expect(await screen.findByText('ok')).toBeInTheDocument()
  })

  it('renders "unreachable" when the health call fails', async () => {
    mockedFetchHealth.mockRejectedValue(new Error('network down'))

    render(<App />)

    expect(await screen.findByText('unreachable')).toBeInTheDocument()
  })
})
