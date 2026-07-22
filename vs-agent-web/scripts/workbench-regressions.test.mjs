import test from 'node:test'
import assert from 'node:assert/strict'
import { parseJsonObject, streamClosureStatus } from '../src/utils/streamLifecycle.js'
import { localizedDescription, productErrorMessage } from '../src/utils/productText.js'

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

test('unavailable optional product APIs are shown as configured-off capabilities', () => {
  assert.equal(
    productErrorMessage({ response: { status: 404 } }, '能力目录'),
    '能力目录暂未接入当前运行环境，可先使用已开启的对话、知识库和工作流能力。'
  )
  assert.equal(productErrorMessage(new Error('network down'), '能力目录'), 'network down')
})

test('dynamic backend descriptions avoid English-only user-facing copy', () => {
  assert.equal(localizedDescription('Generate a PDF file with given content'), '该能力暂未提供中文说明。')
  assert.equal(localizedDescription('生成 PDF 文件'), '生成 PDF 文件')
})
