import test from 'node:test'
import assert from 'node:assert/strict'
import { buildCapabilityCatalog, capabilityStats } from '../src/utils/capabilityCatalog.js'
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

test('capability catalog combines platform tools and skills before optional MCP governance', () => {
  const catalog = buildCapabilityCatalog({
    platformTools: [
      { toolName: 'web_search', displayName: 'WebSearch', sourceType: 'LOCAL', tags: ['search'], requiredParams: ['query'] },
      { toolName: 'mcp_router', displayName: 'McpRouter', sourceType: 'MCP', tags: ['mcp'], requiredParams: ['instruction'] }
    ],
    skills: [
      { name: 'astro-shoot-plan', displayName: '银河拍摄计划', tags: ['astro', 'composite'] }
    ],
    managedTools: []
  })

  assert.deepEqual(catalog.map((item) => item.id), ['tool:web_search', 'tool:mcp_router', 'skill:astro-shoot-plan'])
  assert.deepEqual(catalog.map((item) => item.label), ['网页搜索', 'MCP 路由器', '银河拍摄计划'])
  assert.deepEqual(capabilityStats(catalog), {
    total: 3,
    tools: 2,
    skills: 1,
    mcp: 1,
    managed: 0
  })
})
