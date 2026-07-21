import { useCallback, useEffect, useRef, useState, type ChangeEvent } from 'react'

import { blobToImageFile } from '../lib/imageFile'

interface CameraCaptureProps {
  // All captured (or fallback-picked) files, handed up in one go on "Done".
  onDone: (files: File[]) => void
  // The user closed the camera without adding anything.
  onCancel: () => void
}

type CameraPhase = 'starting' | 'live' | 'error'

interface Shot {
  id: string
  url: string
  file: File
}

/**
 * An in-app live camera. Opens the environment-facing camera into a `<video>`
 * viewfinder; the shutter captures the current frame to a `<canvas>` → `toBlob()` →
 * an `image/*` `File`. Multi-shot: the camera stays open after each capture, frames
 * accumulate as thumbnails, and "Done" hands the whole set up via `onDone` (a single
 * shot + Done is just the `N=1` case).
 *
 * Hard requirement: every acquired `MediaStreamTrack` is stopped on Done, on cancel,
 * and on unmount — no dangling camera indicator or battery drain. Permission-denied /
 * no-device rejects into a clear message plus a file-picker fallback so camera trouble
 * never blocks adding items. No client resize — the backend re-encodes to ≤800px JPEG.
 */
export default function CameraCapture({ onDone, onCancel }: CameraCaptureProps) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const [phase, setPhase] = useState<CameraPhase>('starting')
  const [shots, setShots] = useState<Shot[]>([])
  // Monotonic id source so each captured thumbnail is uniquely keyed.
  const shotCounter = useRef(0)

  // Stop every acquired track and drop the stream handle. Idempotent, so it is safe
  // to call from Done, cancel, and the unmount cleanup without double-stopping.
  const stopStream = useCallback(() => {
    const stream = streamRef.current
    if (stream) {
      stream.getTracks().forEach((track) => track.stop())
      streamRef.current = null
    }
  }, [])

  // Open the camera once on mount. If the component unmounts before the async request
  // resolves, stop the just-acquired stream immediately so nothing leaks.
  useEffect(() => {
    let cancelled = false
    const start = async () => {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: 'environment' },
        })
        if (cancelled) {
          stream.getTracks().forEach((track) => track.stop())
          return
        }
        streamRef.current = stream
        if (videoRef.current) {
          videoRef.current.srcObject = stream
        }
        setPhase('live')
      } catch {
        // Permission denied / no device / insecure context — fall back to the picker.
        if (!cancelled) {
          setPhase('error')
        }
      }
    }
    void start()
    return () => {
      cancelled = true
      stopStream()
    }
  }, [stopStream])

  // Release every accumulated thumbnail URL when leaving the camera.
  const revokeShots = useCallback((list: Shot[]) => {
    list.forEach((shot) => URL.revokeObjectURL(shot.url))
  }, [])

  // Shutter: draw the current viewfinder frame to a canvas and wrap the encoded blob
  // as an image File. The camera stays open so more frames can be captured.
  const capture = () => {
    const video = videoRef.current
    if (!video) {
      return
    }
    const canvas = document.createElement('canvas')
    canvas.width = video.videoWidth || video.clientWidth
    canvas.height = video.videoHeight || video.clientHeight
    const context = canvas.getContext('2d')
    if (!context) {
      return
    }
    context.drawImage(video, 0, 0, canvas.width, canvas.height)
    canvas.toBlob(
      (blob) => {
        if (!blob) {
          return
        }
        const id = `shot-${(shotCounter.current += 1)}`
        const file = blobToImageFile(blob, 'camera')
        setShots((prev) => [...prev, { id, url: URL.createObjectURL(blob), file }])
      },
      'image/jpeg',
      0.9,
    )
  }

  const done = () => {
    stopStream()
    const files = shots.map((shot) => shot.file)
    revokeShots(shots)
    onDone(files)
  }

  const cancel = () => {
    stopStream()
    revokeShots(shots)
    onCancel()
  }

  // Error-state fallback: picking from the library funnels through the same onDone
  // hand-off, so a camera failure still reaches the shared review queue.
  const onFallbackPick = (event: ChangeEvent<HTMLInputElement>) => {
    const files = event.target.files
    if (files && files.length > 0) {
      onDone([...files])
    }
    event.target.value = ''
  }

  if (phase === 'error') {
    return (
      <section className="camera-surface camera-error" aria-label="Camera">
        <p className="state-note">
          Camera unavailable — check permissions, or choose photos from your library.
        </p>
        <label className="photo-picker">
          <span className="field-label">Choose from library</span>
          <input
            type="file"
            accept="image/*"
            multiple
            aria-label="Choose from library"
            onChange={onFallbackPick}
          />
        </label>
        <button type="button" className="btn btn-block" onClick={onCancel}>
          Cancel
        </button>
      </section>
    )
  }

  return (
    <section className="camera-surface" aria-label="Camera">
      <video
        ref={videoRef}
        className="camera-viewfinder"
        data-testid="camera-viewfinder"
        autoPlay
        playsInline
        muted
      />

      {phase === 'starting' && <p className="state-note">Starting camera…</p>}

      {shots.length > 0 && (
        <ul className="camera-shots" aria-label="Captured photos">
          {shots.map((shot) => (
            <li key={shot.id} className="camera-shot">
              <img className="camera-shot-thumb" src={shot.url} alt="Captured garment photo" />
            </li>
          ))}
        </ul>
      )}

      <div className="camera-controls">
        <button
          type="button"
          className="btn camera-shutter"
          aria-label="Capture photo"
          onClick={capture}
          disabled={phase !== 'live'}
        >
          Capture
        </button>
        <div className="camera-actions">
          <button type="button" className="btn" onClick={cancel}>
            Cancel
          </button>
          <button
            type="button"
            className="btn btn-primary"
            onClick={done}
            disabled={shots.length === 0}
          >
            Done{shots.length > 0 ? ` (${shots.length})` : ''}
          </button>
        </div>
      </div>
    </section>
  )
}
