export default {
  name: 'react-distance-budget-acceptance',
  description: 'Verify real-player chunk budgets, cooldown-spaced recovery, missing-telemetry passivity, and exact distance restoration.',
  async run(context) {
    const evidence = context.report.distanceBudget = { snapshots: [], status: 'running' }
    async function qa(operation) {
      const prefix = `REACT_DISTANCE ${operation} `
      const message = await context.command(`/reactqa distance ${operation}`, /^REACT_DISTANCE /, 10_000)
      context.expect(message.startsWith(prefix), 'Distance fixture accepted command', message)
      const snapshot = JSON.parse(message.slice(prefix.length))
      evidence.snapshots.push({ operation, ...snapshot })
      return snapshot
    }
    async function settled() {
      return context.waitUntil(async () => {
        const value = await qa('snapshot')
        return !value.pending ? value : false
      }, { label: 'distance budget calculation applied', timeoutMs: 10_000, intervalMs: 100 })
    }
    async function healthyWindow(refreshBudget = false) {
      let consecutive = 0
      return context.waitUntil(async () => {
        const snapshot = await qa('snapshot')
        consecutive = snapshot.tickAvailable && snapshot.tickTime <= 45 ? consecutive + 1 : 0
        if (refreshBudget && snapshot.tickAvailable) await qa('tick')
        return consecutive >= 10 ? snapshot : false
      }, { label: 'ten consecutive healthy native tick observations', timeoutMs: 60_000, intervalMs: 250 })
    }
    let baseline
    try {
      await context.step('verify one real indexed player and available or explicitly unsupported telemetry', async () => {
        baseline = await context.waitUntil(async () => {
          const value = await qa('snapshot')
          return value.indexReady && value.indexedPlayers === 1 && value.online === 1
            && (!value.tickAvailable || value.tickTime <= 45) ? value : false
        }, { label: 'real-player footprint and stable telemetry', timeoutMs: 30_000, intervalMs: 250 })
        if (baseline.tickAvailable) baseline = await healthyWindow()
        context.expect(baseline.serverView >= 5 && baseline.serverSimulation >= 4,
          'Fixture server ceilings allow a budget reduction and recovery', baseline)
      })
      let constrained
      await context.step('apply overlap-aware budgets without changing unsupported telemetry servers', async () => {
        await qa('begin')
        constrained = await settled()
        if (baseline.tickAvailable) {
          const current = constrained.worlds[constrained.playerWorld]
          context.expect(constrained.tickAvailable && constrained.evaluatedAt > 0, 'Native telemetry allowed an actual budget evaluation', constrained)
          context.expect(current.view === 4 && current.simulation === 3,
            'One real player fits 49 ticking chunks and 32 view-only chunks', constrained)
        } else {
          context.expect(constrained.evaluatedAt === 0 && JSON.stringify(constrained.worlds) === JSON.stringify(baseline.worlds),
            'Unavailable native telemetry preserves every world distance', constrained)
        }
      })
      await context.step('require two healthy evaluations and limit recovery to one chunk', async () => {
        if (baseline.tickAvailable) await healthyWindow(true)
        await context.sleep(1100)
        await qa('recover')
        const first = await settled()
        const before = constrained.worlds[constrained.playerWorld]
        context.expect(JSON.stringify(first.worlds[first.playerWorld]) === JSON.stringify(before),
          'First recovery evaluation does not increase distances', first)
        await qa('tick')
        const immediate = await settled()
        context.expect(immediate.evaluatedAt === first.evaluatedAt,
          'Repeated calls inside the cooldown do not count as recovery checks', immediate)
        await context.sleep(1100)
        await qa('tick')
        const second = await settled()
        if (baseline.tickAvailable) {
          const after = second.worlds[second.playerWorld]
          context.expect(second.tickAvailable && second.tickTime <= 45, 'Recovery uses healthy native tick telemetry', second)
          context.expect(after.view === before.view + 1 && after.simulation === before.simulation + 1,
            'Second healthy evaluation increases each distance by exactly one chunk', second)
        } else {
          context.expect(second.evaluatedAt === 0 && JSON.stringify(second.worlds) === JSON.stringify(baseline.worlds),
            'Recovery stays passive without native telemetry', second)
        }
      })
      await context.step('restore all original distances on deactivation', async () => {
        const restored = await qa('cleanup')
        context.expect(!restored.active && JSON.stringify(restored.worlds) === JSON.stringify(baseline.worlds),
          'Every world returns to its exact pre-fixture settings', restored)
        evidence.status = 'verified'
      })
    } finally {
      if (!context.signal.aborted) await qa('cleanup')
    }
  },
}
