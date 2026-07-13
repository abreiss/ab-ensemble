export interface HealthStatus {
  status: string
}

/**
 * Calls the backend health endpoint. Resolves with the parsed body on a 2xx
 * response; rejects on a non-2xx response or a network/transport failure so the
 * caller can surface an "unreachable" state.
 */
export async function fetchHealth(): Promise<HealthStatus> {
  const response = await fetch('/api/health')
  if (!response.ok) {
    throw new Error(`Health request failed with status ${response.status}`)
  }
  return (await response.json()) as HealthStatus
}
