export default {
  name: 'react-entity-uninstalled-acceptance',
  description: 'Verify saved mob AI and awareness after React is removed and the server restarts.',
  async run(context) {
    context.bot.chat('/gamemode creative')
    await context.waitUntil(() => context.bot.game.gameMode === 'creative', { timeoutMs: 10_000, label: 'creative mode' })
    context.bot.creative.startFlying()
    context.bot.entity.velocity.set(0, 0, 0)
    await context.command('/tp @s 1024 120 1024', /teleported/i)
    await context.waitUntil(() => Object.values(context.bot.entities).filter(entity => entity.name === 'cow').length >= 4, {
      timeoutMs: 30_000, label: 'persisted test mobs visible after removal',
    })
    for (const ai of [true, false]) {
      for (const aware of [true, false]) {
        const tag = `react-lifecycle-${ai ? 1 : 0}${aware ? 1 : 0}`
        await context.step(`verify ${tag} without React`, async () => {
          const match = `@e[tag=${tag},limit=1]`
          const checks = [
            `${ai ? 'unless' : 'if'} data entity @s {NoAI:1b} if data entity @s {"Bukkit.Aware":${aware ? 1 : 0}b}`,
            'unless data entity @s BukkitValues."react:react-paused"',
            'unless data entity @s BukkitValues."react:react-dozing"',
          ]
          for (const [index, condition] of checks.entries()) {
            const expected = `REACT_REMOVED_${ai ? 1 : 0}${aware ? 1 : 0}_${index}`
            await context.command(`/execute as ${match} ${condition} run say ${expected}`, new RegExp(expected), 10_000)
          }
        })
      }
    }
  },
}
