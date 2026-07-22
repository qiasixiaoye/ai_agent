<template>
  <aside class="inspector-panel" aria-label="上下文检查器">
    <WorkbenchSection title="当前会话">
      <dl class="inspector-details">
        <div><dt>会话</dt><dd>{{ conversationId || '尚未创建' }}</dd></div>
        <div><dt>模式</dt><dd>{{ modeLabel }}</dd></div>
      </dl>
    </WorkbenchSection>

    <WorkbenchSection title="记忆建议">
      <p class="muted">对话产生可复用信息时，会在这里生成候选记忆。确认后再写入长期记忆。</p>
      <textarea id="memory-content" v-model="suggestionContent" rows="4" placeholder="暂无候选记忆"></textarea>
      <button type="button" :disabled="!conversationId || !suggestionContent.trim() || memory.writing" @click="writeMemory">
        {{ memory.writing ? '写入中…' : '写入记忆' }}
      </button>
      <p v-if="memory.suggestion?.status === 'failed'" class="status-error">{{ memory.error || '记忆写入失败。' }}</p>
      <p v-else-if="memory.suggestion?.status === 'written'" class="status-success">记忆已写入。</p>
    </WorkbenchSection>

    <WorkbenchSection title="上下文预览">
      <p class="muted">{{ previewQuery || '发送消息后可查看将进入模型的上下文摘要。' }}</p>
      <button type="button" :disabled="!conversationId || !previewQuery || previewing" @click="previewContext">
        {{ previewing ? '加载中…' : '预览上下文' }}
      </button>
      <dl v-if="memory.contextPreview" class="inspector-details">
        <div><dt>预算</dt><dd>{{ memory.contextPreview.estimatedTokens }} / {{ memory.contextPreview.tokenBudget }}</dd></div>
        <div><dt>层级</dt><dd>{{ (memory.contextPreview.includedTiers || []).join('、') || '无' }}</dd></div>
      </dl>
    </WorkbenchSection>

    <WorkbenchSection title="权限摘要">
      <p class="muted">能力中心会在高风险工具调用前要求确认。</p>
      <dl class="inspector-details">
        <div><dt>运行态</dt><dd>{{ statusLabel(runtime.status) }}</dd></div>
        <div><dt>工具数</dt><dd>{{ runtime.tools.length }}</dd></div>
      </dl>
      <p v-if="runtime.unavailable" class="muted">当前环境未开启 MCP 治理接口。</p>
    </WorkbenchSection>

    <WorkbenchSection title="最近动作">
      <p v-if="!workbench.recentInvocations.length" class="muted">暂无调用记录。</p>
      <ul v-else class="invocation-list">
        <li v-for="invocation in workbench.recentInvocations" :key="invocation.id">
          <strong>{{ invocation.name || invocation.operation || invocation.source }}</strong>
          <span>{{ statusLabel(invocation.status) }} · {{ invocation.summary }}</span>
        </li>
      </ul>
    </WorkbenchSection>
  </aside>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import WorkbenchSection from './WorkbenchSection.vue'
import { useChatStore } from '../../stores/chat'
import { useMemoryStore } from '../../stores/memory'
import { useRuntimeStore } from '../../stores/runtime'
import { useWorkbenchStore } from '../../stores/workbench'
import { statusLabel } from '../../utils/productText'

const chatStore = useChatStore()
const memory = useMemoryStore()
const runtime = useRuntimeStore()
const workbench = useWorkbenchStore()
const previewing = ref(false)
const suggestionContent = ref('')

const conversationId = computed(() => workbench.currentConversationId)
const chatMessages = computed(() => chatStore.assistantAppChats[conversationId.value]?.messages || [])
const lastUserMessage = computed(() => [...chatMessages.value].reverse().find((message) => message.isUser)?.content || '')
const previewQuery = computed(() => lastUserMessage.value.trim())
const modeLabel = computed(() => ({
  normal: '普通对话',
  rag: '知识库',
  agent: '智能体'
}[workbench.currentMode] || '普通对话'))

watch(
  () => memory.suggestion,
  (suggestion) => {
    suggestionContent.value = suggestion?.content || ''
  },
  { immediate: true, deep: true }
)

watch(conversationId, (id) => {
  if (id) memory.loadConversation(id)
}, { immediate: true })

onMounted(() => {
  runtime.loadHealth()
  runtime.loadTools()
})

const writeMemory = async () => {
  memory.updateSuggestion({ content: suggestionContent.value.trim(), importance: 0.8 })
  try {
    await memory.writeSuggestion(conversationId.value)
  } catch {
    // 错误由 store 暴露在当前卡片里。
  }
}

const previewContext = async () => {
  previewing.value = true
  try {
    await memory.previewContext(conversationId.value, previewQuery.value)
  } finally {
    previewing.value = false
  }
}
</script>

<style scoped>
.inspector-panel { display: grid; align-content: start; gap: 12px; overflow-y: auto; }
.inspector-panel :deep(.workbench-section) { padding: 14px; box-shadow: none; }
.inspector-panel textarea { width: 100%; margin-top: 10px; padding: 9px 10px; color: var(--color-text); background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); resize: vertical; }
.inspector-panel button { width: 100%; margin-top: 10px; padding: 8px 10px; color: #07111f; background: linear-gradient(135deg, var(--color-primary), var(--color-primary-strong)); border: 0; border-radius: var(--radius-sm); font-weight: 800; }
.inspector-panel button:disabled { color: var(--color-text-subtle); background: var(--color-panel-muted); cursor: not-allowed; }
.inspector-details { display: grid; gap: 7px; margin: 0; font-size: 0.8rem; }
.inspector-details div { display: grid; grid-template-columns: 64px minmax(0, 1fr); gap: 8px; }
.inspector-details dt { color: var(--color-text-subtle); }
.inspector-details dd { min-width: 0; margin: 0; overflow-wrap: anywhere; }
.status-error { margin: 8px 0 0; color: var(--color-danger); font-size: 0.78rem; }
.status-success { margin: 8px 0 0; color: var(--color-success); font-size: 0.78rem; }
.invocation-list { display: grid; gap: 8px; margin: 0; padding: 0; list-style: none; }
.invocation-list li { display: grid; gap: 2px; font-size: 0.78rem; }
.invocation-list span { color: var(--color-text-muted); overflow-wrap: anywhere; }

@media (max-width: 900px) {
  .inspector-panel { max-height: 52vh; }
}
</style>
