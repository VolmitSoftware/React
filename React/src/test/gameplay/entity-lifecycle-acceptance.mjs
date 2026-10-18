export default {
  name: 'react-entity-lifecycle-acceptance',
  description: 'Restore only React-owned AI and awareness before chunk serialization and normal removal.',
  async run(context) {
    const evidence = context.report.reactEntityLifecycle = { snapshots: [], status: 'running' }
    async function qa(operation) {
      const message = await context.command(`/reactqa lifecycle ${operation}`, /^REACT_LIFECYCLE /, 10_000)
      const prefix = `REACT_LIFECYCLE ${operation} `
      context.expect(message.startsWith(prefix), 'Lifecycle fixture accepted command', message)
      return JSON.parse(message.slice(prefix.length))
    }
    function restored(states) {
      return Object.keys(states).length === 4 && Object.entries(states).every(([tag, state]) =>
        state.ai === (tag.at(-2) === '1') && state.aware === (tag.at(-1) === '1') && !state.paused && !state.dozing)
    }
    context.bot.chat('/gamemode creative')
    await context.waitUntil(() => context.bot.game.gameMode === 'creative', { timeoutMs: 10_000, label: 'creative mode' })
    context.bot.creative.startFlying()
    context.bot.entity.velocity.set(0, 0, 0)
    await context.command('/react monitoring-only', /monitoring/i)
    await context.command('/tp @s 1024 120 1024', /teleported/i)
    await context.waitUntil(() => Math.abs(context.bot.entity.position.x - 1024) < 2, { timeoutMs: 15_000, label: 'fixture location' })
    await context.command('/kill @e[type=minecraft:cow,distance=..16]', /killed|no entity|no entities/i)
    await context.step('claim enabled AI and awareness while preserving disabled state', async () => {
      const snapshot = await qa('prepare')
      context.expect(Object.keys(snapshot.loaded).length === 4, 'Four independent original states were prepared', snapshot)
      for (const [tag, state] of Object.entries(snapshot.loaded)) {
        context.expect(!state.ai && !state.aware && state.paused === (tag.at(-2) === '1') && state.dozing === (tag.at(-1) === '1'), 'React claims only state it changes', { tag, state })
      }
      evidence.snapshots.push(snapshot)
    })
    await context.step('unload restores state before serialization', async () => {
      await context.command('/tp @s 4096 120 4096', /teleported/i)
      const snapshot = await context.waitUntil(async () => {
        const value = await qa('snapshot')
        return restored(value.unloaded) ? value : false
      }, { timeoutMs: 90_000, intervalMs: 500, label: 'four restored unload snapshots' })
      evidence.snapshots.push(snapshot)
    })
    await context.step('disk reload retains original state without ownership markers', async () => {
      await context.command('/tp @s 1024 120 1024', /teleported/i)
      const snapshot = await context.waitUntil(async () => {
        const value = await qa('snapshot')
        return restored(value.loaded) ? value : false
      }, { timeoutMs: 30_000, intervalMs: 250, label: 'four restored disk reload snapshots' })
      evidence.snapshots.push(snapshot)
      await context.waitUntil(() => Object.values(context.bot.entities).filter(entity => entity.name === 'cow').length >= 4, {
        timeoutMs: 15_000, label: 'restored mobs visible to the client',
      })
    })
    await context.step('leave owned state for normal shutdown restoration', async () => {
      const snapshot = await qa('pause')
      context.expect(Object.values(snapshot.loaded).every(state => !state.ai && !state.aware), 'All test mobs are paused before shutdown', snapshot)
      evidence.snapshots.push(snapshot)
    })
    evidence.status = 'verified'
  },
}
