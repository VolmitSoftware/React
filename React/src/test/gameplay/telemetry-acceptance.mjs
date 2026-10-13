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
        if (context.bot.game.gameMode !== 'creative') {
          await context.command(`/gamemode creative ${context.bot.username}`, /game mode|already/i, 5000)
        }
        await context.command(`/tp ${context.bot.username} 8 180 8`, /teleported/i, 5000)
        await context.command('/fill 6 179 6 14 179 14 stone', /filled|no blocks/i, 5000)
        const initial = await qa('begin 64')
        context.expect(initial.active && initial.spawned === 64, 'All fixture entities exist', { spawned: initial.spawned, active: initial.active })
      })
      await context.step('observe live measurements with monotonically advancing capture sequence', async () => {
        let previous = 0
        let previousSequence = 0
        let previousCapture = 0
        const positive = new Set()
        for (let index = 0; index < 12; index++) {
          await context.sleep(1000)
          const state = await qa('snapshot')
          context.expect(state.active && state.samples > previous, 'Entity sampling progresses', { current: state.samples, previous })
          context.expect(state.history.sequence > previousSequence && state.history.capturedAtMs > previousCapture && state.history.count > 100, 'History publishes live metric snapshots')
          const values = Object.fromEntries(state.history.values.map(value => [value.id, value]))
          for (const id of ['memory-pressure', 'jvm-heap-utilization', 'react-nearby-queries', 'react-nearby-candidates', 'react-nearby-samples']) {
            const value = values[id]
            context.expect(value && value.available && Number.isFinite(value.value) && value.value >= 0, `Metric ${id} is available`, value)
            context.expect(value.sampledAtMs > 0 && value.metadata.intervalMs === 1000, `Metric ${id} retains source time and interval`, value)
            const expectedUnit = id === 'memory-pressure' ? 'bytes/s' : id === 'jvm-heap-utilization' ? '%' : '/s'
            context.expect(value.metadata.unit === expectedUnit && value.metadata.quality === (id === 'memory-pressure' ? 'ESTIMATED' : 'MEASURED'), `Metric ${id} declares its raw unit and quality`, value)
          }
          previousSequence = state.history.sequence
          previousCapture = state.history.capturedAtMs
          for (const id of ['react-nearby-queries', 'react-nearby-candidates', 'react-nearby-samples']) {
            if (values[id]?.value > 0) positive.add(id)
          }
          previous = state.samples
          evidence.snapshots.push(state)
        }
        context.expect(positive.size === 3, 'Every neighborhood acquisition counter observes work', [...positive])
        let regional = await qa('snapshot region-tps-lowest,region-player-coverage,region-players-below-18-tps')
        let regionValues = Object.fromEntries(regional.history.values.map(value => [value.id, value]))
        if (regional.folia) {
          await context.waitUntil(async () => {
            regional = await qa('snapshot region-tps-lowest,region-player-coverage,region-players-below-18-tps')
            regionValues = Object.fromEntries(regional.history.values.map(value => [value.id, value]))
            return regionValues['region-tps-lowest']?.available && regionValues['region-player-coverage']?.value === 100
          }, { label: 'Folia player region publishes its TPS window', timeoutMs: 20_000, intervalMs: 1000 })
        }
        evidence.regional = regional
        context.expect(Object.keys(regionValues).length === 3, 'Regional metrics are registered on both platforms')
        if (regional.folia) {
          context.expect(regionValues['region-tps-lowest'].available && regionValues['region-tps-lowest'].value > 0, 'Folia provides observed regional TPS', regionValues)
          context.expect(regionValues['region-player-coverage'].available && regionValues['region-player-coverage'].value === 100, 'The sole player has a current region observation', regionValues)
        } else {
          context.expect(!regionValues['region-tps-lowest'].available, 'Paper does not report invented regional TPS', regionValues)
        }
        evidence.status = 'verified'
      })
    } finally {
      if (!context.signal.aborted) await qa('cleanup')
    }
  },
}
