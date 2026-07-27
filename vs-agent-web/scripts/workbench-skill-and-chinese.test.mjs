import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const chat = readFileSync(new URL('../src/views/workbench/ChatWorkspace.vue', import.meta.url), 'utf8')
const memory = readFileSync(new URL('../src/views/workbench/MemoryWorkspace.vue', import.meta.url), 'utf8')

test('workbench chat uses the real Skill execution path', () => {
  assert.match(chat, /executeSkill/)
  assert.match(chat, /astroShootPlanSample/)
  assert.match(chat, /previewSkillRoute/)
})

test('memory workspace displays Chinese labels', () => {
  const template = memory.split('<script setup>')[0]
  assert.doesNotMatch(template, /Reload memory|Manual semantic memory|Conversation memory|Context preview/)
  assert.match(memory, /\u5bf9\u8bdd\u8bb0\u5fc6/)
  assert.match(memory, /\u91cd\u65b0\u52a0\u8f7d\u8bb0\u5fc6/)
})
