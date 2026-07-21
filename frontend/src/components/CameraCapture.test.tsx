import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import CameraCapture from './CameraCapture'

// The camera surface is deliberately thin on view plumbing (per docs/TESTING.md we
// do not over-test `<video>`/canvas rendering). The behaviour that MUST be proven is
// the hard requirement the spec calls out: every acquired MediaStreamTrack is
// stopped on Done, on cancel, and on unmount — no dangling camera. We also prove the
// permission/no-device fallback and the multi-shot capture → onDone(files) path.

// A fake MediaStream whose tracks expose spy `stop()`s so we can assert teardown.
function makeStream() {
  const tracks = [{ stop: vi.fn() }, { stop: vi.fn() }]
  return { tracks, stream: { getTracks: () => tracks } as unknown as MediaStream }
}

const getUserMedia = vi.fn()

beforeEach(() => {
  getUserMedia.mockReset()
  // jsdom exposes no `navigator.mediaDevices`; define a controllable one.
  Object.defineProperty(navigator, 'mediaDevices', {
    configurable: true,
    value: { getUserMedia },
  })
  // jsdom's canvas is inert: stub the 2D context + toBlob so the shutter yields a
  // deterministic Blob without a real rendering backend.
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
    drawImage: vi.fn(),
  } as unknown as CanvasRenderingContext2D)
  vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation(function (
    this: HTMLCanvasElement,
    cb: BlobCallback,
  ) {
    cb(new Blob([new Uint8Array([1, 2, 3])], { type: 'image/jpeg' }))
  })
  // jsdom has no object-URL support; stub distinct URLs per call for thumbnails.
  let n = 0
  vi.stubGlobal('URL', {
    ...URL,
    createObjectURL: vi.fn(() => `blob:shot-${n++}`),
    revokeObjectURL: vi.fn(),
  })
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('CameraCapture', () => {
  it('stops every acquired MediaStreamTrack on unmount (no dangling camera)', async () => {
    // Arrange
    const { tracks, stream } = makeStream()
    getUserMedia.mockResolvedValue(stream)

    // Act
    const { unmount } = render(<CameraCapture onDone={vi.fn()} onCancel={vi.fn()} />)
    // Wait for the live viewfinder (getUserMedia resolved).
    await screen.findByRole('button', { name: /capture/i })
    unmount()

    // Assert: teardown stopped every track.
    tracks.forEach((track) => expect(track.stop).toHaveBeenCalledTimes(1))
  })

  it('stops every track and calls onCancel when the camera is cancelled', async () => {
    // Arrange
    const { tracks, stream } = makeStream()
    getUserMedia.mockResolvedValue(stream)
    const onCancel = vi.fn()

    // Act
    render(<CameraCapture onDone={vi.fn()} onCancel={onCancel} />)
    fireEvent.click(await screen.findByRole('button', { name: /cancel/i }))

    // Assert
    tracks.forEach((track) => expect(track.stop).toHaveBeenCalledTimes(1))
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  it('captures multiple frames then Done hands every file to onDone and stops tracks', async () => {
    // Arrange
    const { tracks, stream } = makeStream()
    getUserMedia.mockResolvedValue(stream)
    const onDone = vi.fn()

    // Act
    render(<CameraCapture onDone={onDone} onCancel={vi.fn()} />)
    const shutter = await screen.findByRole('button', { name: /capture/i })
    fireEvent.click(shutter) // shot 1
    fireEvent.click(shutter) // shot 2 — camera stays open (multi-shot)
    fireEvent.click(screen.getByRole('button', { name: /^done/i }))

    // Assert: both captured frames arrive as image Files, and the stream is torn down.
    expect(onDone).toHaveBeenCalledTimes(1)
    const files = onDone.mock.calls[0][0] as File[]
    expect(files).toHaveLength(2)
    files.forEach((file) => {
      expect(file).toBeInstanceOf(File)
      expect(file.type).toMatch(/^image\//)
    })
    tracks.forEach((track) => expect(track.stop).toHaveBeenCalledTimes(1))
  })

  it('shows a clear message and a file-picker fallback when getUserMedia is denied', async () => {
    // Arrange: permission-denied / no-device rejects the stream request.
    getUserMedia.mockRejectedValue(new DOMException('denied', 'NotAllowedError'))
    const onDone = vi.fn()

    // Act
    render(<CameraCapture onDone={onDone} onCancel={vi.fn()} />)

    // Assert: a message plus a "Choose from library" fallback control (never a dead end).
    expect(await screen.findByText(/camera unavailable/i)).toBeInTheDocument()
    const fallback = screen.getByLabelText(/choose from library/i)
    expect(fallback).toBeInTheDocument()

    // The fallback routes picked files through the same onDone hand-off.
    const file = new File([new Uint8Array([9])], 'lib.jpg', { type: 'image/jpeg' })
    fireEvent.change(fallback, { target: { files: [file] } })
    await waitFor(() => expect(onDone).toHaveBeenCalledTimes(1))
    expect((onDone.mock.calls[0][0] as File[])[0]).toBe(file)
  })
})
