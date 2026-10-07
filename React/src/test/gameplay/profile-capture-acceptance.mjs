export default {
  name: 'react-profile-capture-acceptance',
  description: 'Verify local JFR events, bounded output, concurrent capture rejection, and recorder cancellation.',
  async run(context) {
    const evidence = context.report.profileCapture = { snapshots: [] }
    async function qa(operation) {
      const prefix = `REACT_PROFILE ${operation} `
      const message = await context.command(`/reactqa profile ${operation}`, /^REACT_PROFILE /, 10_000)
      context.expect(message.startsWith(prefix), 'Profile fixture accepted command', message)
      return JSON.parse(message.slice(prefix.length))
    }
    try {
      await context.step('capture a readable local profile and reject overlap', async () => {
        await qa('begin')
        await context.waitUntil(async () => (await qa('snapshot')).recordings === 1,
          { label: 'JFR recorder active', timeoutMs: 20_000, intervalMs: 100 })
        await qa('overlap')
        const snapshot = await context.waitUntil(async () => {
          const value = await qa('snapshot')
          return value.inspected ? value : false
        }, { label: 'local recording inspected', timeoutMs: 30_000, intervalMs: 100 })
        context.expect(snapshot.done && !snapshot.failed && snapshot.count === 1, 'Exactly one profile completed', snapshot)
        context.expect(snapshot.overlapFailed, 'Concurrent capture rejected', snapshot)
        context.expect(snapshot.local && snapshot.bytes > 0 && snapshot.bytes <= 64 * 1024 * 1024, 'Output stays local and bounded', snapshot)
        context.expect((snapshot.events['jdk.ExecutionSample'] ?? 0) + (snapshot.events['jdk.NativeMethodSample'] ?? 0) > 0, 'Profile contains execution samples', snapshot.events)
        context.expect((snapshot.events['jdk.ObjectAllocationSample'] ?? 0) > 0, 'Profile contains allocation samples', snapshot.events)
        for (const name of ['jdk.InitialSystemProperty', 'jdk.InitialEnvironmentVariable', 'jdk.InitialSecurityProperty', 'jdk.JVMInformation', 'jdk.SystemProcess']) {
          context.expect(!snapshot.events[name], 'Environment and process arguments are excluded', { name })
        }
        context.expect(snapshot.recordings === 0, 'Completed recorder closes', snapshot)
        evidence.snapshots.push(snapshot)
      })
      await context.step('cancel an active profile and release recording resources', async () => {
        await qa('long')
        await context.waitUntil(async () => (await qa('snapshot')).recordings === 1,
          { label: 'second recorder active', timeoutMs: 20_000, intervalMs: 100 })
        await qa('cancel')
        const snapshot = await context.waitUntil(async () => {
          const value = await qa('snapshot')
          return value.recordings === 0 ? value : false
        }, { label: 'cancelled recorder closed', timeoutMs: 10_000, intervalMs: 100 })
        context.expect(snapshot.done && snapshot.failed && snapshot.count === 0 && !snapshot.path, 'Cancellation cannot publish success', snapshot)
        evidence.snapshots.push(snapshot)
      })
      await context.step('public capture command completes with a local output path', async () => {
        const message = await context.command('/react action capture-profile seconds=1', /Saved 1 local profile to .*\.jfr/, 30_000)
        context.expect(message.includes('diagnostics') && message.includes('profiles'), 'Public command reports the canonical capture directory', message)
        evidence.commandCompletion = message
      })
    } finally {
      if (!context.signal.aborted) await qa('cleanup')
    }
  },
}
