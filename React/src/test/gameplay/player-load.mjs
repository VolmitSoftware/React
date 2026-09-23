import { randomBytes } from 'node:crypto'

export function assertMetrics(snapshot, expectedPlayers) {
  for (const id of ['players', 'chunks', 'entities', 'tick-time']) {
    const metric = snapshot.metrics?.[id]
    if (!metric) throw new Error(`Missing React ${id} metric`)
    if (!metric.registered || !metric.available || !metric.valid || !Number.isFinite(metric.value) || metric.value < 0) throw new Error(`React ${id} metric unavailable or invalid on ${snapshot.server}`)
  }
  if (snapshot.metrics.players.value !== expectedPlayers || snapshot.online !== expectedPlayers) throw new Error('React player count does not match connected actors')
}

export default {
  name: 'react-player-load',
  description: 'Four ordinary players walk, place, and mine concurrently while checking real React sampler availability and values on Paper.',
  async run(context) {
    const command = text => context.command(text, /filled|no blocks|teleported|game mode|gave|removed|nothing changed/i, 10_000)
    const actors = []
    const evidence = context.report.react = { actors: [], snapshots: [], coverage: 'Paper live metrics under four-player gameplay; no Folia or optimization efficacy claim' }
    let sequence = 0
    async function snapshot() {
      const prefix = `REACT_QA s${++sequence} `
      return JSON.parse((await context.command(`/reactqa s${sequence}`, new RegExp(`^${prefix}`), 5000)).slice(prefix.length))
    }
    const at = (bot, x, y, z) => bot.entity.position.clone().set(x, y, z)
    const equip = async (bot, name) => { await context.waitUntil(() => bot.inventory.items().some(item => item.name === name), { label: name }); await bot.equip(bot.inventory.items().find(item => item.name === name), 'hand') }
    await context.step('prepare four ordinary players and verify React samplers', async () => {
      if (context.bot.game.gameMode !== 'spectator') await command(`/gamemode spectator ${context.bot.username}`)
      await command(`/tp ${context.bot.username} 24 105 0`)
      await context.waitUntil(() => context.bot.blockAt(at(context.bot, -8, 99, -8)) && context.bot.blockAt(at(context.bot, 56, 99, 8)), { label: 'arena chunks' })
      await command('/fill -8 99 -8 56 99 8 stone')
      await command('/fill -8 100 -8 56 104 8 air')
      const suffix = randomBytes(3).toString('hex')
      for (let index = 0; index < 4; index++) {
        const actor = await context.connectActor(`RQA${index}${suffix}`)
        actors.push(actor)
        evidence.actors.push(actor.bot.username)
        if (actor.bot.game.gameMode !== 'survival') await command(`/gamemode survival ${actor.bot.username}`)
        await command(`/tp ${actor.bot.username} ${index * 16 + 4.5} 100 .5`)
        for (const item of ['iron_pickaxe', 'stone_bricks 8']) await command(`/give ${actor.bot.username} ${item}`)
      }
      const baseline = await context.waitUntil(async () => { const value = await snapshot(); return value.metrics.players.value === 5 && value.actors.length === 4 ? value : false }, { label: 'React sees all ordinary players', timeoutMs: 20_000, intervalMs: 500 })
      assertMetrics(baseline, 5)
      evidence.snapshots.push(baseline)
    })
    await context.step('four concurrent walking circuits with metric samples', async () => {
      const active = actors.map((actor, index) => actor.actions.walkCircle({ center: { x: index * 16 + .5, y: 100, z: .5 }, radius: 4, laps: 2, timeoutMs: 60_000 }))
      const monitoring = (async () => { for (let index = 0; index < 8; index++) { await context.sleep(500); const value = await snapshot(); assertMetrics(value, 5); evidence.snapshots.push(value) } })()
      await Promise.all([...active, monitoring])
    })
    await context.step('concurrent real block placements and mining', async () => {
      for (let index = 0; index < actors.length; index++) await command(`/tp ${actors[index].bot.username} ${index * 16 + .5} 100 .5`)
      await Promise.all(actors.map(async (actor, index) => {
        for (let iteration = 0; iteration < 8; iteration++) {
          const position = at(actor.bot, index * 16 + 2, 100, 0)
          await equip(actor.bot, 'stone_bricks')
          await actor.bot.placeBlock(actor.bot.blockAt(position.offset(0, -1, 0)), at(actor.bot, 0, 1, 0))
          await context.waitUntil(() => actor.bot.blockAt(position)?.name === 'stone_bricks', { label: 'placed block' })
          await equip(actor.bot, 'iron_pickaxe')
          await actor.bot.dig(actor.bot.blockAt(position))
          await context.waitUntil(() => actor.bot.blockAt(position)?.name === 'air', { label: 'mined block' })
        }
      }))
      const first = evidence.snapshots[0]
      const final = await context.waitUntil(async () => {
        const value = await snapshot()
        return value.breaks - first.breaks >= 32 && value.placements - first.placements >= 32 ? value : false
      }, { label: 'server accepted all player block actions', timeoutMs: 5000, intervalMs: 100 })
      assertMetrics(final, 5)
      context.expect(final.breaks - first.breaks === 32 && final.placements - first.placements === 32 && final.moves - first.moves >= 100, 'Server observed all 64 player block interactions and movement', final)
      evidence.snapshots.push(final)
    })
  }
}
