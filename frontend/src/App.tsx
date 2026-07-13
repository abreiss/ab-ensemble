import { useEffect, useState } from 'react'
import { fetchHealth } from './api/health'

type HealthState = 'loading' | 'unreachable' | (string & {})

export default function App() {
  const [status, setStatus] = useState<HealthState>('loading')

  useEffect(() => {
    let active = true
    fetchHealth()
      .then((health) => {
        if (active) setStatus(health.status)
      })
      .catch(() => {
        if (active) setStatus('unreachable')
      })
    return () => {
      active = false
    }
  }, [])

  return (
    <main>
      <h1>Ensemble</h1>
      <p>
        Backend status: <strong>{status}</strong>
      </p>
    </main>
  )
}
