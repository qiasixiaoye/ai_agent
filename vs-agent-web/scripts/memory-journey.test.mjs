import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const memory = readFileSync(new URL('../src/views/workbench/MemoryWorkspace.vue', import.meta.url), 'utf8')
const chat = readFileSync(new URL('../src/views/workbench/ChatWorkspace.vue', import.meta.url), 'utf8')
const agent = readFileSync(new URL('../src/views/workbench/AgentWorkspace.vue', import.meta.url), 'utf8')
const store = readFileSync(new URL('../src/stores/memory.js', import.meta.url), 'utf8')
const inspector = readFileSync(new URL('../src/components/workbench/InspectorPanel.vue', import.meta.url), 'utf8')

test('memory journey provides a Chinese automatic-write example', () => {
  assert.match(memory, /记忆如何工作/)
  assert.match(memory, /体验自动写入示例/)
  assert.match(chat, /MEMORY_EXAMPLE_MESSAGE/)
  assert.match(agent, /开发调试/)
  assert.match(store, /this\.memoryStatus = 'written'/)
  assert.match(inspector, /semanticMemories/)
})
