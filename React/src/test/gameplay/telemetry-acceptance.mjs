export default {
  name: 'react-telemetry-acceptance',
  description: 'Verify sampled telemetry, source metadata, neighborhood acquisition counters and active history on Paper and Folia.',
  async run(context) {
    const evidence = context.report.telemetry = { snapshots: [], status: 'running' }
    async function qa(operation) {
      const response = await context.command(`/reactqa telemetry ${operation}`, /^REACT_TELEMETRY /, 15_000)
      const prefix = `REACT_TELEMETRY ${operation.split(' ')[0]} `
      context.expect(response.startsWith(prefix), 'Telemetry fixture accepted operation', response.slice(0, 500))
      return JSON.parse(response.slice(prefix.length))
    }
    try {
      await context.step('start a bounded neighborhood workload', async () => {
        await context.command(`/gamemode creative ${context.bot.username}`, /game mode|already/i, 5000)
        await context.command(`/tp ${context.bot.username} 8 180 8`, /teleported/i, 5000)
        const initial = await qa('begin 128')
        context.expect(initial.active && initial.spawned === 128, 'All fixture entities exist', { spawned: initial.spawned, active: initial.active })
      })
      await context.step('observe live measurements with monotonically advancing capture sequence', async () => {
        let previous = 0
        for (let index = 0; index < 12; index++) {
          await context.sleep(1000)
          const state = await qa('snapshot')
          context.expect(state.active && state.samples > previous, 'Entity sampling progresses', { current: state.samples, previous })
          context.expect(state.history.sequence > 0 && state.history.values.length > 100, 'History publishes live metric snapshots')
          const values = Object.fromEntries(state.history.values.map(value => [value.id, value]))
          for (const id of ['memory-pressure', 'jvm-heap-utilization', 'react-nearby-queries', 'react-nearby-candidates', 'react-nearby-samples']) {
            const value = values[id]
            context.expect(value && value.available && Number.isFinite(value.value) && value.value >= 0, `Metric ${id} is available`, value)
          }
          previous = state.samples
          evidence.snapshots.push(state)
        }
        evidence.status = 'verified'
      })
    } finally {
      if (!context.signal.aborted) await qa('cleanup')
    }
  },
}
