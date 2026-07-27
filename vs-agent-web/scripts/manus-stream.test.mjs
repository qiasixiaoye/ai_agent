import test from 'node:test'
import assert from 'node:assert/strict'
import { applyManusStreamEvent, createManusStreamState } from '../src/utils/manusStream.js'

test('keeps final answer text out of the Manus execution trace', () => {
  let state = createManusStreamState()
  state = applyManusStreamEvent(state, { type: 'thinking', step: 1, text: 'searching' })
  state = applyManusStreamEvent(state, { type: 'tool_call', step: 1, toolName: 'web_search' })
  state = applyManusStreamEvent(state, { type: 'answer', step: 2, text: 'result' })

  assert.equal(state.answerText, 'result')
  assert.deepEqual(state.trace.map(item => item.type), ['thinking', 'tool_call'])
  assert.equal(state.terminal, false)
})

test('closes only on a named terminal event', () => {
  let state = createManusStreamState()
  state = applyManusStreamEvent(state, { type: 'thinking', step: 1, text: 'working' })
  assert.equal(state.terminal, false)

  state = applyManusStreamEvent(state, { type: 'complete', step: 1 })
  assert.equal(state.terminal, true)
  assert.equal(state.errorMessage, '')
})
