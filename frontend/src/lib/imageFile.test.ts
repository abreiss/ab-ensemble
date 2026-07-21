import { describe, expect, it } from 'vitest'

import { blobToImageFile } from './imageFile'

// Paste and camera both produce raw `Blob`s (a pasted screenshot, a canvas
// capture) that may lack a usable name or MIME type. `blobToImageFile` normalises
// them into a valid `image/*` `File` the existing upload pipeline can send as-is —
// the backend re-encodes to ≤800px JPEG, so no client resize is needed here.
describe('blobToImageFile', () => {
  it('wraps a blob as a File, preserving its image/* type and synthesizing a name', () => {
    // Arrange
    const blob = new Blob([new Uint8Array([1, 2, 3])], { type: 'image/jpeg' })

    // Act
    const file = blobToImageFile(blob)

    // Assert: a real File whose bytes/type survive and whose name is synthesized.
    expect(file).toBeInstanceOf(File)
    expect(file.type).toBe('image/jpeg')
    expect(file.name).toMatch(/^pasted-\d+\.jpeg$/)
    expect(file.size).toBe(3)
  })

  it('falls back to image/png when the blob reports no type', () => {
    // Arrange: a clipboard screenshot can arrive with an empty MIME type.
    const blob = new Blob([new Uint8Array([1])], { type: '' })

    // Act
    const file = blobToImageFile(blob)

    // Assert
    expect(file.type).toBe('image/png')
    expect(file.name).toMatch(/^pasted-\d+\.png$/)
  })

  it('falls back to image/png when the blob type is not an image', () => {
    // Arrange: guard against a non-image type slipping through.
    const blob = new Blob(['x'], { type: 'application/octet-stream' })

    // Act
    const file = blobToImageFile(blob)

    // Assert
    expect(file.type).toBe('image/png')
  })

  it('uses the supplied name prefix and derives the extension from the type', () => {
    // Arrange
    const blob = new Blob([new Uint8Array([1])], { type: 'image/webp' })

    // Act
    const file = blobToImageFile(blob, 'camera')

    // Assert
    expect(file.name).toMatch(/^camera-\d+\.webp$/)
    expect(file.type).toBe('image/webp')
  })
})
