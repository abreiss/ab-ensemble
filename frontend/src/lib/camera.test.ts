import { afterEach, describe, expect, it } from 'vitest'

import { cameraSupported } from './camera'

// `cameraSupported` gates the "Take photos" control: present only where the browser
// exposes `navigator.mediaDevices.getUserMedia` (a secure context), otherwise the
// screen degrades to the always-present file picker.
describe('cameraSupported', () => {
  const original = Object.getOwnPropertyDescriptor(navigator, 'mediaDevices')

  afterEach(() => {
    if (original) {
      Object.defineProperty(navigator, 'mediaDevices', original)
    } else {
      // jsdom exposes no mediaDevices by default; remove any test-added stub.
      Reflect.deleteProperty(navigator as unknown as Record<string, unknown>, 'mediaDevices')
    }
  })

  it('is true when navigator.mediaDevices.getUserMedia is available', () => {
    // Arrange: a secure-context browser exposes getUserMedia.
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: () => Promise.resolve({} as MediaStream) },
    })

    // Act + Assert
    expect(cameraSupported()).toBe(true)
  })

  it('is false when mediaDevices is absent (insecure context / unsupported)', () => {
    // Arrange: no mediaDevices (the jsdom / insecure-origin default).
    Reflect.deleteProperty(navigator as unknown as Record<string, unknown>, 'mediaDevices')

    // Act + Assert
    expect(cameraSupported()).toBe(false)
  })

  it('is false when mediaDevices exists but getUserMedia is not a function', () => {
    // Arrange: guard the partial-support edge (mediaDevices present, no getUserMedia).
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: {},
    })

    // Act + Assert
    expect(cameraSupported()).toBe(false)
  })
})
