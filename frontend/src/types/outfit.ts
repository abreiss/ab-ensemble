/**
 * A saved outfit as persisted by the backend (`/api/outfits`) — mirrors the
 * `OutfitResponse` DTO. Deliberately named `SavedOutfit`, **not** `Outfit`, to
 * avoid colliding with the ephemeral stylist-pick `Outfit` interface in
 * `api/style.ts`. `reason` is populated for AI-picked looks (`source: 'ai'`)
 * and `null` for hand-built ("manual") ones.
 */
export interface SavedOutfit {
  outfitId: string
  itemIds: string[]
  source: 'ai' | 'manual'
  reason: string | null
  createdAt: string
}
