import test from 'node:test'
import assert from 'node:assert/strict'
import { assertMetrics } from './player-load.mjs'

test('metric evidence rejects missing, invalid, and unsupported samplers instead of reporting zero', () => {
  const snapshot = { server: 'Paper', online: 5, metrics: { players: { registered: true, available: true, valid: true, value: 5 }, chunks: { registered: true, available: true, valid: true, value: 12 }, entities: { registered: true, available: true, valid: true, value: 5 }, 'tick-time': { registered: true, available: true, valid: true, value: 10 } } }
  assert.doesNotThrow(() => assertMetrics(snapshot, 5))
  for (const bad of [{ registered: false }, { available: false }, { valid: false }, { value: NaN }, { value: -1 }, { value: undefined }]) {
    assert.throws(() => assertMetrics({ ...snapshot, metrics: { ...snapshot.metrics, 'tick-time': { ...snapshot.metrics['tick-time'], ...bad } } }, 5))
  }
  assert.throws(() => assertMetrics(snapshot, 4))
  assert.throws(() => assertMetrics({ ...snapshot, metrics: {} }, 5))
})

const { assertConservation } = await import('./intervention-comparison.mjs')
test('paired comparison independently rejects lost items and protected or logical mob changes', () => {
  const state = { inventory: { cobblestone: 8, dirt: 8 }, ground: { cobblestone: 8, dirt: 8 }, logicalCows: 6, protectedAlive: true, protectedClaim: true, protectedCount: 1 }
  assert.doesNotThrow(() => assertConservation(state))
  for (const patch of [{ ground: { cobblestone: 7, dirt: 8 } }, { logicalCows: 5 }, { protectedAlive: false }, { protectedClaim: false }, { protectedCount: 2 }]) assert.throws(() => assertConservation({ ...state, ...patch }))
})
