import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { buildCapabilityCatalog, capabilityStats } from '../src/utils/capabilityCatalog.js'
import { parseJsonObject, streamClosureStatus } from '../src/utils/streamLifecycle.js'
import { localizedDescription, productErrorMessage } from '../src/utils/productText.js'
import { buildWorkbenchSnapshot, restoreWorkbenchSnapshot } from '../src/utils/workbenchPersistence.js'
import { applyWorkbenchSnapshotToStores, shouldPersistWorkbenchChats } from '../src/utils/workbenchSession.js'
import { useChatStore } from '../src/stores/chat.js'

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

test('capability catalog assigns functional domains and permission levels', () => {
  const catalog = buildCapabilityCatalog({
    platformTools: [
      { toolName: 'web_search', sourceType: 'LOCAL', tags: ['search', 'retrieval'] },
      { toolName: 'mcp_router', sourceType: 'MCP', tags: ['mcp', 'tool-calling'] }
    ],
    skills: [
      { name: 'pdf-generation', tags: ['file', 'document'] },
      { name: 'astro-shoot-plan', tags: ['astro', 'photography', 'composite'] }
    ]
  })

  assert.deepEqual(
    catalog.map((item) => [item.name, item.functionGroup, item.permission.level]),
    [
      ['web_search', '检索与资料', 'external_read'],
      ['mcp_router', '外部 MCP', 'confirm'],
      ['pdf-generation', '文档与文件', 'local_write'],
      ['astro-shoot-plan', '天文摄影', 'direct']
    ]
  )
  assert.deepEqual(catalog.find((item) => item.name === 'astro-shoot-plan').dependentTools, [
    'milkyway_rise',
    'light_pollution',
    'cloud_cover'
  ])
})

test('workbench persistence restores the active session without storing unbounded transcript data', () => {
  const snapshot = buildWorkbenchSnapshot({
    workbench: {
      currentConversationId: 'conv-1',
      currentMode: 'rag',
      activeArea: 'chat',
      inspectorOpen: false
    },
    chats: {
      'conv-1': {
        id: 'conv-1',
        mode: 'rag',
        createdAt: '2026-07-22T00:00:00.000Z',
        messages: Array.from({ length: 45 }, (_, index) => ({
          id: `m-${index}`,
          content: index === 44 ? '长内容'.repeat(3000) : `消息 ${index}`,
          isUser: index % 2 === 0,
          role: index % 2 === 0 ? 'user' : 'assistant',
          mode: 'rag',
          status: 'complete',
          timestamp: '2026-07-22T00:00:00.000Z'
        }))
      }
    }
  })

  assert.equal(snapshot.currentConversationId, 'conv-1')
  assert.equal(snapshot.currentMode, 'rag')
  assert.equal(snapshot.ui.inspectorOpen, false)
  assert.equal(snapshot.conversations[0].messages.length, 40)
  assert.equal(snapshot.conversations[0].messages.at(-1).content.length, 4001)
  assert.equal(snapshot.conversations[0].messages.at(-1).content.endsWith('…'), true)

  const restored = restoreWorkbenchSnapshot(JSON.stringify(snapshot))
  assert.equal(restored.currentConversationId, 'conv-1')
  assert.equal(restored.conversations[0].messages.length, 40)
})

test('workbench persistence preserves conversation category and pin state', () => {
  const snapshot = buildWorkbenchSnapshot({
    workbench: { currentConversationId: 'conv-managed' },
    chats: {
      'conv-managed': {
        id: 'conv-managed',
        title: '研究 MCP',
        mode: 'agent',
        category: 'agent',
        pinned: true,
        messages: []
      }
    }
  })

  assert.equal(snapshot.conversations[0].category, 'agent')
  assert.equal(snapshot.conversations[0].pinned, true)
  const restored = restoreWorkbenchSnapshot(JSON.stringify(snapshot))
  assert.equal(restored.conversations[0].category, 'agent')
  assert.equal(restored.conversations[0].pinned, true)
})

test('clearing a conversation retains metadata while deleting removes it', () => {
  setActivePinia(createPinia())
  const chatStore = useChatStore()
  const chatId = chatStore.createConversation('rag', { title: '知识整理', category: 'rag', pinned: true })
  chatStore.addMessage(chatId, { content: '保留这条原始消息', isUser: true })

  chatStore.clearConversation(chatId)
  assert.equal(chatStore.assistantAppChats[chatId].title, '知识整理')
  assert.equal(chatStore.assistantAppChats[chatId].category, 'rag')
  assert.equal(chatStore.assistantAppChats[chatId].pinned, true)
  assert.deepEqual(chatStore.assistantAppChats[chatId].messages, [])

  assert.equal(chatStore.deleteConversation(chatId), true)
  assert.equal(chatStore.assistantAppChats[chatId], undefined)
})

test('workbench session restore is layout-level and independent of the chat page', () => {
  const snapshot = restoreWorkbenchSnapshot(JSON.stringify({
    version: 1,
    currentConversationId: 'conv-capabilities',
    currentMode: 'agent',
    activeArea: 'capabilities',
    ui: { inspectorOpen: true },
    conversations: [{
      id: 'conv-capabilities',
      title: '刷新后仍然存在',
      mode: 'agent',
      createdAt: '2026-07-23T00:00:00.000Z',
      updatedAt: '2026-07-23T00:00:00.000Z',
      messages: [{ id: 'm1', content: 'hello', isUser: true, timestamp: '2026-07-23T00:00:00.000Z' }]
    }]
  }))
  const chatStore = {
    hydrated: [],
    hydrateAssistantAppChats(conversations) {
      this.hydrated = conversations
    }
  }
  const workbench = {
    currentConversationId: '',
    restored: null,
    restorePersistedState(payload) {
      this.restored = payload
      this.currentConversationId = payload.currentConversationId
    }
  }

  assert.equal(applyWorkbenchSnapshotToStores(snapshot, { chatStore, workbench }), true)
  assert.equal(workbench.currentConversationId, 'conv-capabilities')
  assert.equal(chatStore.hydrated[0].messages[0].content, 'hello')
  assert.equal(shouldPersistWorkbenchChats({}), false)
  assert.equal(shouldPersistWorkbenchChats({ 'conv-capabilities': { id: 'conv-capabilities', messages: [] } }), true)
})
