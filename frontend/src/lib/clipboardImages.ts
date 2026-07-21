import { blobToImageFile } from './imageFile'

// Two complementary ways to pull images off the clipboard, plus a capability probe
// for the async path. The synchronous `paste`-event path works everywhere Cmd/Ctrl-V
// is pressed on the screen; the async `navigator.clipboard.read()` path powers an
// explicit "Paste image" button but is gated behind a user gesture and browser
// support. Both funnel through `blobToImageFile` so every extracted image reaches
// the queue as a valid `image/*` `File`.

/**
 * Extracts every image `File` from a paste event's `DataTransfer` (the synchronous
 * `ClipboardEvent.clipboardData` path). Non-image entries (text, etc.) are ignored.
 * Returns an empty array when there is no clipboard data.
 */
export function imageFilesFromClipboardData(
  data: DataTransfer | null | undefined,
): File[] {
  const items = data?.items
  if (!items) {
    return []
  }
  const files: File[] = []
  // `DataTransferItemList` is array-like but not iterable everywhere — index it.
  for (let i = 0; i < items.length; i += 1) {
    const item = items[i]
    if (item.kind === 'file' && item.type.startsWith('image/')) {
      const file = item.getAsFile()
      if (file) {
        files.push(blobToImageFile(file))
      }
    }
  }
  return files
}

/**
 * Extracts every image `File` from async `ClipboardItem`s (from
 * `navigator.clipboard.read()`). For each item it picks the first `image/*` type,
 * reads its blob via `getType`, and wraps it as a `File`. Non-image items yield
 * nothing.
 */
export async function imageFilesFromClipboardItems(
  items: ClipboardItem[],
): Promise<File[]> {
  const files: File[] = []
  for (const item of items) {
    const imageType = item.types.find((type) => type.startsWith('image/'))
    if (imageType) {
      const blob = await item.getType(imageType)
      files.push(blobToImageFile(blob))
    }
  }
  return files
}

/**
 * Whether the async clipboard-read path (the "Paste image" button) is usable:
 * `navigator.clipboard.read` and `ClipboardItem` must exist, and — where the newer
 * static probe is available — `ClipboardItem.supports('image/png')` must be true.
 * The `paste`-event path does not depend on this.
 */
export function clipboardReadSupported(): boolean {
  if (typeof navigator === 'undefined' || typeof navigator.clipboard?.read !== 'function') {
    return false
  }
  if (typeof ClipboardItem === 'undefined') {
    return false
  }
  if (typeof ClipboardItem.supports === 'function') {
    return ClipboardItem.supports('image/png')
  }
  // Older implementations expose async read + ClipboardItem without the static
  // probe; their presence is the best signal we have.
  return true
}
