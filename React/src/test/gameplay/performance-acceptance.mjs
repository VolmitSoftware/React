export default {
  name: 'react-performance-acceptance',
  description: 'Verify bounded entity rotation and real map pixels, plus staged playbook completion and recovery with controlled evidence and a held child.',
  async run(context) {
    const evidence = context.report.reactPerformance = {
      coverage: 'Paper/Folia entity ownership and map packet acceptance; controlled playbook lifecycle, not mitigation efficacy or capacity',
      snapshots: [], mapPackets: {}, status: 'running',
    }
    const mapPackets = new Map()
    const onMap = packet => {
      const id = packet.itemDamage ?? packet.mapId ?? packet.id
      if (Number.isInteger(id) && packet.data?.length > 0) mapPackets.set(id, (mapPackets.get(id) ?? 0) + 1)
    }
    context.bot._client.on('map', onMap)
    async function qa(operation) {
      const prefix = `REACT_PERFORMANCE ${operation} `
      const message = await context.command(`/reactqa performance ${operation}`, /^REACT_PERFORMANCE /, 10_000)
      context.expect(message.startsWith(prefix), 'Performance fixture accepted command', message)
      return JSON.parse(message.slice(prefix.length))
    }
    try {
      await context.step('prepare owner region and inspect live pressure telemetry', async () => {
        if (context.bot.game.gameMode !== 'creative') {
          await context.command(`/gamemode creative ${context.bot.username}`, /game mode|already/i, 5000)
        }
        await context.command(`/tp ${context.bot.username} 0.5 180 0.5`, /teleported/i, 5000)
        await context.command('/fill -2 179 -2 4 179 8 stone', /filled|no blocks/i, 5000)
        evidence.telemetry = await qa('ready')
      })
      let initial
      await context.step('observe real entities and three independent map ids', async () => {
        const snapshot = await qa('begin')
        context.expect(snapshot.standIds.length === 3 && snapshot.standIds.every(id => snapshot.sampledIds.includes(id)), 'Sampler covers every fixture entity within bounded rotations', snapshot)
        context.expect(new Set(snapshot.mapIds).size === 3, 'Three independently tracked frame maps exist', snapshot)
        await context.waitUntil(() => snapshot.standIds.every(id => Object.values(context.bot.entities).some(entity => entity.uuid === id)), { label: 'client sees all sampled armor stands', timeoutMs: 10_000 })
        evidence.snapshots.push(snapshot)
        initial = snapshot
      })
      await context.step('map wall delivers pixel packets while child action remains running', async () => {
        await context.bot.lookAt(context.bot.entity.position.clone().set(1.5, 181.5, 4.5), true)
        if (initial.controlledPressure) {
          const running = await context.waitUntil(async () => {
            const value = await qa('snapshot')
            return value.childRunning && value.actionCalls['action-hopper-network-normalize'] > 0 ? value : false
          }, { label: 'held child running under parent', timeoutMs: 15_000, intervalMs: 250 })
          context.expect(!running.done && running.completedActions === 0, 'Parent does not report queued child as completed', running)
          evidence.snapshots.push(running)
        }
        await context.waitUntil(() => initial.mapIds.every(id => mapPackets.has(id)), { label: 'all three maps delivered real pixels', timeoutMs: 20_000, intervalMs: 250 })
      })
      await context.step('finish child and recover before further intervention', async () => {
        if (initial.controlledPressure) await qa('release')
        const completed = await context.waitUntil(async () => {
          const value = await qa('snapshot')
          return value.done ? value : false
        }, { label: 'playbook terminal after real child completion and recheck', timeoutMs: 15_000, intervalMs: 250 })
        context.expect(!completed.failed && completed.completedActions === (initial.controlledPressure ? 1 : 0), 'Available telemetry completes one controlled child; missing telemetry stays passive', completed)
        for (const id of ['action-trim-entities-by-age-priority', 'action-quarantine-hot-chunks', 'action-prewarm-critical-chunks', 'collect-garbage']) {
          context.expect(completed.actionCalls[id] === 0, 'Recovery prevents unrelated or amplifying mitigation', { id, calls: completed.actionCalls[id] })
        }
        evidence.snapshots.push(completed)
        evidence.mapPackets = Object.fromEntries(mapPackets)
        evidence.status = 'verified'
      })
    } finally {
      context.bot._client.removeListener('map', onMap)
      if (!context.signal.aborted) await qa('cleanup')
    }
  },
}
