export default {
  name: 'react-async-prewarm-acceptance',
  description: 'Verify real asynchronous prewarming, generation permission, already-loaded chunks, and cancellation of warmup continuations.',
  async run(context) {
    const evidence = context.report.reactAsyncPrewarm = { coverage: 'Real chunk loading with controlled hotspot selection; precise cancellation races are covered by unit tests; native generation already requested cannot be recalled', snapshots: [], status: 'running' }
    async function qa(operation) {
      const message = await context.command(`/reactqa prewarm ${operation}`, /^REACT_PREWARM /, 10_000)
      const prefix = `REACT_PREWARM ${operation} `
      context.expect(message.startsWith(prefix), 'Prewarm fixture accepted command', message)
      return JSON.parse(message.slice(prefix.length))
    }
    async function waitFor(label, predicate, timeoutMs = 60_000) {
      const snapshot = await context.waitUntil(async () => {
        const value = await qa('snapshot')
        context.expect(!value.inspection.error, 'Owner inspection has no error', value)
        return predicate(value) ? value : false
      }, { label, timeoutMs, intervalMs: 250 })
      evidence.snapshots.push(snapshot)
      return snapshot
    }
    try {
      await context.step('missing chunks remain ungenerated when generation is disabled', async () => {
        await qa('prepare')
        const initial = await waitFor('unexplored target inspected', value => value.inspection.ready)
        context.expect(!initial.inspection.generated && !initial.inspection.loaded, 'Target starts ungenerated and unloaded', initial)
        await qa('missing')
        const result = await waitFor('existing-only prewarm completes', value => value.done)
        context.expect(!result.failed && result.warmed === 0 && result.loaded === 0 && result.inFlight === 0, 'Missing chunk was skipped without generation', result)
        context.expect(!result.inspection.generated, 'Existing-only prewarm never generates terrain', result)
      })
      await context.step('generation-enabled prewarm waits for loaded owner finishing', async () => {
        await qa('generate')
        const result = await waitFor('generation and warmup complete', value => value.done && value.inspection.generated)
        context.expect(!result.failed && result.warmed === 1 && result.loaded === 1 && result.processed === 1 && result.inFlight === 0, 'One new chunk loaded and warmed before terminal success', result)
      })
      await context.step('an already-loaded player chunk does not count as a new load', async () => {
        await qa('existing')
        const result = await waitFor('loaded chunk warmup complete', value => value.done)
        context.expect(result.playerChunks && !result.failed && result.warmed === 1 && result.loaded === 0 && result.inFlight === 0, 'Player-index selection warms an existing chunk without another load', result)
      })
      await context.step('cancellation invalidates outstanding warmup work', async () => {
        await qa('cancel')
        const result = await waitFor('cancelled target inspected', value => value.inspection.ready && value.done)
        context.expect(result.failed && result.warmed === 0 && result.loaded === 0 && result.inFlight === 0, 'Cancelled action releases its pending slot', result)
      })
      evidence.status = 'verified'
    } finally {
      if (!context.signal.aborted) await qa('cleanup')
    }
  },
}
