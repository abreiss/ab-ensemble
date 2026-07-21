import { describe, expect, it } from 'vitest'

import { runWithConcurrency } from './promisePool'

describe('runWithConcurrency', () => {
  it('never runs more than the limit (3) at once while completing every task in order', async () => {
    // Arrange: each task records how many are live so we can observe the peak. The
    // limit is a structural cap, so peak can never exceed it; with more tasks than
    // the limit it must also *reach* it.
    let live = 0
    let peak = 0
    const task = (value: number) => async () => {
      live += 1
      peak = Math.max(peak, live)
      await new Promise((resolve) => setTimeout(resolve, 1))
      live -= 1
      return value
    }
    const tasks = Array.from({ length: 9 }, (_, i) => task(i))

    // Act
    const results = await runWithConcurrency(tasks, 3)

    // Assert: exactly 3 ran simultaneously (never more), all 9 completed, in order.
    expect(peak).toBe(3)
    expect(results).toHaveLength(9)
    expect(results.map((r) => (r.status === 'fulfilled' ? r.value : null))).toEqual([
      0, 1, 2, 3, 4, 5, 6, 7, 8,
    ])
  })

  it('surfaces each task’s result or rejection independently (one failure never aborts the rest)', async () => {
    // Arrange: a middle task rejects; the others must still resolve.
    const boom = new Error('boom')
    const tasks = [
      async () => 'a',
      async () => {
        throw boom
      },
      async () => 'c',
    ]

    // Act
    const results = await runWithConcurrency(tasks, 2)

    // Assert
    expect(results[0]).toEqual({ status: 'fulfilled', value: 'a' })
    expect(results[1]).toEqual({ status: 'rejected', reason: boom })
    expect(results[2]).toEqual({ status: 'fulfilled', value: 'c' })
  })

  it('resolves to an empty array for no tasks', async () => {
    // Arrange / Act
    const results = await runWithConcurrency([], 3)

    // Assert
    expect(results).toEqual([])
  })

  it('defaults the limit to 3 when none is given', async () => {
    // Arrange: 6 tasks with the default limit should still peak at 3.
    let live = 0
    let peak = 0
    const task = () => async () => {
      live += 1
      peak = Math.max(peak, live)
      await new Promise((resolve) => setTimeout(resolve, 1))
      live -= 1
    }

    // Act
    await runWithConcurrency(Array.from({ length: 6 }, task))

    // Assert
    expect(peak).toBe(3)
  })
})
