import { useRegisterSW } from 'virtual:pwa-register/react'

export default function ServiceWorkerRegistration() {
  useRegisterSW({ immediate: true })
  return null
}
