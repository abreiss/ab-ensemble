import { CATEGORY_LABELS, normalizeCategory, sectionOrder } from './categoryTaxonomy'
import type { Category } from './categoryTaxonomy'
import type { Item } from '../types/item'

/**
 * One rendered wardrobe-grid section: a taxonomy bucket, its display label,
 * and the items that landed in it, in original list order.
 */
export interface WardrobeSection {
  category: Category
  label: string
  items: Item[]
}

/**
 * Pure, React-free grouping/ordering for the `/wardrobe` grid (spec Unit 3):
 * buckets each item by applying `normalizeCategory` to its **stored**
 * `category` at read time (no item is mutated), then returns one section per
 * non-empty bucket in the fixed taxonomy order with `Other` always last.
 * Empty sections are omitted entirely so the grid never renders a header
 * with no items under it.
 */
export function groupByCategory(items: Item[]): WardrobeSection[] {
  const buckets = new Map<Category, Item[]>()
  for (const it of items) {
    const bucket = normalizeCategory(it.category)
    const existing = buckets.get(bucket)
    if (existing) {
      existing.push(it)
    } else {
      buckets.set(bucket, [it])
    }
  }

  const sections: WardrobeSection[] = []
  for (const category of sectionOrder()) {
    const bucketItems = buckets.get(category)
    if (bucketItems && bucketItems.length > 0) {
      sections.push({ category, label: CATEGORY_LABELS[category], items: bucketItems })
    }
  }
  return sections
}
