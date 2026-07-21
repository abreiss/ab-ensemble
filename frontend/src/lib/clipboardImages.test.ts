import { afterEach, describe, expect, it, vi } from 'vitest'

import {
  clipboardReadSupported,
  imageFilesFromClipboardData,
  imageFilesFromClipboardItems,
} from './clipboardImages'

afterEach(() => {
  vi.unstubAllGlobals()
})

// A `DataTransferItem` for the synchronous paste-event path.
function fileItem(type: string) {
  return {
    kind: 'file' as const,
    type,
    getAsFile: () => new File([new Uint8Array([1, 2, 3])], 'clip', { type }),
  }
}

function stringItem(type = 'text/plain') {
  return { kind: 'string' as const, type, getAsFile: () => null }
}

describe('imageFilesFromClipboardData (paste-event path)', () => {
  it('extracts every image file and filters out non-image entries', () => {
    // Arrange: a paste carrying two images and one text entry.
    const data = { items: [fileItem('image/png'), stringItem(), fileItem('image/jpeg')] }

    // Act
    const files = imageFilesFromClipboardData(data as unknown as DataTransfer)

    // Assert: both images are wrapped as valid image Files; the text entry is dropped.
    expect(files).toHaveLength(2)
    expect(files.every((f) => f instanceof File && f.type.startsWith('image/'))).toBe(true)
  })

  it('returns an empty array when there is no clipboard data', () => {
    // Arrange / Act / Assert
    expect(imageFilesFromClipboardData(null)).toEqual([])
    expect(imageFilesFromClipboardData(undefined)).toEqual([])
  })
})

describe('imageFilesFromClipboardItems (async read path)', () => {
  it('reads all image ClipboardItems via getType and wraps them as image Files', async () => {
    // Arrange: two async ClipboardItems, one PNG image and one plain-text.
    const pngBlob = new Blob([new Uint8Array([1, 2])], { type: 'image/png' })
    const items = [
      { types: ['image/png'], getType: vi.fn(async () => pngBlob) },
      {
        types: ['text/plain'],
        getType: vi.fn(async () => new Blob(['x'], { type: 'text/plain' })),
      },
    ]

    // Act
    const files = await imageFilesFromClipboardItems(items as unknown as ClipboardItem[])

    // Assert: only the image item yields a File, wrapped with its reported type.
    expect(files).toHaveLength(1)
    expect(files[0]).toBeInstanceOf(File)
    expect(files[0].type).toBe('image/png')
  })
})

describe('clipboardReadSupported (capability gate)', () => {
  it('is false when the async clipboard read API is absent', () => {
    // Arrange: a navigator with no clipboard.read.
    vi.stubGlobal('navigator', {})

    // Act / Assert
    expect(clipboardReadSupported()).toBe(false)
  })

  it('is true when async read + ClipboardItem image support are present', () => {
    // Arrange
    vi.stubGlobal('navigator', { clipboard: { read: vi.fn() } })
    vi.stubGlobal(
      'ClipboardItem',
      class {
        static supports() {
          return true
        }
      },
    )

    // Act / Assert
    expect(clipboardReadSupported()).toBe(true)
  })

  it('is false when ClipboardItem reports no image/png support', () => {
    // Arrange
    vi.stubGlobal('navigator', { clipboard: { read: vi.fn() } })
    vi.stubGlobal(
      'ClipboardItem',
      class {
        static supports() {
          return false
        }
      },
    )

    // Act / Assert
    expect(clipboardReadSupported()).toBe(false)
  })
})
