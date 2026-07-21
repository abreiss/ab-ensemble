// Paste and camera capture both hand us raw `Blob`s — a pasted screenshot, a
// `<canvas>.toBlob()` frame — that may lack a filename or even a MIME type. This
// helper normalises any such blob into a valid `image/*` `File` the existing
// upload pipeline can send unchanged. No client-side resize is done here: the
// backend `ImageProcessor` decodes → resizes to ≤800px → re-encodes JPEG on save.

/**
 * Wraps a `Blob` as an `image/*` `File` with a synthesized, uniquely-suffixed name
 * and a guaranteed image type. A missing or non-image blob type falls back to
 * `image/png` (the typical clipboard-screenshot format). The file extension is
 * derived from the resolved type so the name stays consistent with its contents.
 *
 * @param blob the source bytes (from clipboard or canvas)
 * @param namePrefix leading segment of the synthesized filename (default `pasted`)
 */
export function blobToImageFile(blob: Blob, namePrefix = 'pasted'): File {
  const type = blob.type && blob.type.startsWith('image/') ? blob.type : 'image/png'
  const extension = type.slice('image/'.length) || 'png'
  const name = `${namePrefix}-${Date.now()}.${extension}`
  return new File([blob], name, { type })
}
