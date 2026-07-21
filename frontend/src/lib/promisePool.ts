// A minimal concurrency limiter, pure and independent of React. The Add screen
// uses it to throttle the per-photo `tagPreview` fan-out so a large batch issues
// only a bounded number of simultaneous vision requests and the rest queue until a
// slot frees — keeping the batch economical against the server-side daily cap.

/**
 * Runs an array of async task thunks with at most `limit` running at once.
 *
 * Results come back in task order with each task's outcome preserved (like
 * `Promise.allSettled`), so a single rejection never aborts the pool — every task
 * still runs and callers can inspect per-task success/failure. `limit` is clamped
 * to at least 1.
 */
export async function runWithConcurrency<T>(
  tasks: Array<() => Promise<T>>,
  limit = 3,
): Promise<PromiseSettledResult<T>[]> {
  const results = new Array<PromiseSettledResult<T>>(tasks.length)
  let next = 0

  // Each worker pulls the next unclaimed task index until the queue is drained.
  // Running `workerCount` of them in parallel caps live tasks at the limit.
  const worker = async (): Promise<void> => {
    while (next < tasks.length) {
      const index = next++
      try {
        results[index] = { status: 'fulfilled', value: await tasks[index]() }
      } catch (reason) {
        results[index] = { status: 'rejected', reason }
      }
    }
  }

  const workerCount = Math.min(Math.max(limit, 1), tasks.length)
  await Promise.all(Array.from({ length: workerCount }, worker))
  return results
}
