import test from 'node:test'
import assert from 'node:assert/strict'
import { astroShootPlanSample, resolveAutoSkill, selectAutoSkill } from '../src/utils/conversationalSkill.js'

test('provides a real astro-shoot-plan payload for the chat sample', () => {
  assert.deepEqual(astroShootPlanSample(), {
    name: 'astro-shoot-plan',
    arguments: { latitude: 39.9042, longitude: 116.4074, date: '2026-08-15' }
  })
})

test('uses the first matched Skill and ignores routing misses', () => {
  assert.equal(selectAutoSkill({ matched: true, selectedSkillNames: ['astro-shoot-plan'] }), 'astro-shoot-plan')
  assert.equal(selectAutoSkill({ matched: false, selectedSkillNames: ['astro-shoot-plan'] }), null)
})

test('only auto-runs the astro Skill when its structured arguments are present', () => {
  const route = { matched: true, selectedSkillNames: ['astro-shoot-plan'] }
  assert.deepEqual(resolveAutoSkill(route, '帮我做银河拍摄计划，纬度39.9042 经度116.4074 2026-08-15'), astroShootPlanSample())
  assert.equal(resolveAutoSkill(route, '帮我做银河拍摄计划'), null)
})
