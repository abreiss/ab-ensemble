// Capability probe for the in-app live camera, kept in `lib/` (not the component)
// so the component file exports only a component — matching `clipboardReadSupported`
// in `clipboardImages.ts` and the repo's pure-helper-in-lib pattern.

/**
 * Whether an in-app live camera is usable. Requires `navigator.mediaDevices`, which
 * the browser only exposes in a secure context (HTTPS in prod, `localhost` in dev),
 * so its absence also covers the insecure-origin degrade path. When this is false the
 * caller keeps the always-present file picker as the baseline acquisition source.
 */
export function cameraSupported(): boolean {
  return (
    typeof navigator !== 'undefined' &&
    typeof navigator.mediaDevices?.getUserMedia === 'function'
  )
}
