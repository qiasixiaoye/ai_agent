import test from 'node:test'
import assert from 'node:assert/strict'
import { parseJsonObject, streamClosureStatus } from '../src/utils/streamLifecycle.js'

test('an SSE closure after response content completes the message', () => {
  assert.equal(streamClosureStatus('A streamed response'), 'complete')
})

test('an SSE failure before response content remains an error', () => {
  assert.equal(streamClosureStatus(''), 'error')
})

test('platform argument validation only accepts JSON objects', () => {
  assert.deepEqual(parseJsonObject('{"query":"Spring AI"}'), { query: 'Spring AI' })
  assert.throws(() => parseJsonObject('[]'), /JSON object/)
})
