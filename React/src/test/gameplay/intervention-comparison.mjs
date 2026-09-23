import { randomBytes } from 'node:crypto'

export function assertConservation(snapshot) {
  for (const material of ['cobblestone', 'dirt']) {
    const total = (snapshot.inventory[material] ?? 0) + (snapshot.ground[material] ?? 0)
    if (total !== 16) throw new Error(`Lost or duplicated ${material}: ${total} instead of 16`)
  }
  if (snapshot.logicalCows !== 6 || !snapshot.protectedAlive || !snapshot.protectedClaim || snapshot.protectedCount !== 1) throw new Error('Cow conservation or protected entity identity failed')
}

export default {
  name: 'react-intervention-comparison',
  description: 'Pair identical seeded player-drop workloads with monitoring-only mode on and off; verify item/mob conservation and protected cow identity.',
  async run(context) {
    const suffix = randomBytes(3).toString('hex')
    const actors = [await context.connectActor(`RQAc0${suffix}`), await context.connectActor(`RQAc1${suffix}`)]
    const evidence = context.report.reactComparison = { status: 'running', seed: 424242, roundIntervalMs: 250, rounds: 4, phases: [], scope: 'Item bundling and mob stacking conservation/protection; no throughput or capacity claim' }
    async function qa(command) {
      const prefix = `REACT_COMPARISON ${command.split(' ')[0]} `
      const message = await context.command(`/reactqa comparison ${command}`, /^REACT_COMPARISON /, 5000)
      context.expect(message.startsWith(prefix), 'Comparison fixture command accepted', message)
      return JSON.parse(message.slice(prefix.length))
    }
    const original = await qa('snapshot')
    async function mode(monitoringOnly) {
      if ((await qa('snapshot')).monitoringOnly !== monitoringOnly) await context.command('/react monitoring-only', /monitoring.only/i, 5000)
      await context.waitUntil(async () => (await qa('snapshot')).monitoringOnly === monitoringOnly, { label: 'requested intervention mode' })
    }
    try {
      for (const monitoringOnly of [true, false]) {
        await context.step(`same-seed workload with interventions ${monitoringOnly ? 'disabled' : 'enabled'}`, async () => {
          await mode(monitoringOnly)
          const before = await qa(`begin ${actors.map(actor => actor.bot.username).join(' ')}`)
          context.expect(before.seed === evidence.seed && before.monitoringOnly === monitoringOnly, 'Fixture seed and intervention mode match')
          for (const actor of actors) await context.waitUntil(() => actor.bot.inventory.items().some(item => item.name === 'dirt'), { label: 'paired fixture inventory' })
          const phase = { monitoringOnly, before, requestedIntervalMs: evidence.roundIntervalMs, dropTimesMs: [] }
          const start = performance.now()
          for (let round = 0; round < evidence.rounds; round++) {
            const delay = start + round * evidence.roundIntervalMs - performance.now()
            if (delay > 0) await context.sleep(delay)
            phase.dropTimesMs.push(Math.round(performance.now() - start))
            await Promise.all(actors.map(async actor => {
              await actor.bot.look(0, 0, true)
              for (const material of ['cobblestone', 'dirt']) await actor.bot.toss(actor.bot.registry.itemsByName[material].id, null, 1)
            }))
          }
          await context.sleep(2500)
          const after = await qa('snapshot')
          assertConservation(after)
          for (const material of ['cobblestone', 'dirt']) context.expect(after.ground[material] === 8 && after.inventory[material] === 8, 'All sixteen player drops reached the ground without loss or pickup', after)
          context.expect(after.protectedCow === before.protectedCow, 'Protected cow UUID remains unchanged')
          if (monitoringOnly) context.expect(after.bundles === 0 && after.cowEntities === 6, 'Disabled interventions leave items unbundled and cows unstacked')
          else context.expect(after.bundles > 0 && after.unprotectedMaxStack > 1, 'Enabled interventions actually bundle items and stack unprotected cows')
          phase.after = after
          evidence.phases.push(phase)
          await qa('cleanup')
        })
      }
      evidence.status = 'verified'
    } finally {
      if (!context.signal.aborted) {
        await qa('cleanup')
        await mode(original.monitoringOnly)
      }
    }
  }
}
