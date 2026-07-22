<template>
  <aside class="inspector-panel" aria-label="Context inspector">
    <WorkbenchSection title="Conversation">
      <dl class="inspector-details">
        <div><dt>Id</dt><dd>{{ conversationId || 'No conversation' }}</dd></div>
        <div><dt>Mode</dt><dd>{{ activeMode }}</dd></div>
      </dl>
    </WorkbenchSection>

    <WorkbenchSection title="Memory suggestion">
      <label for="memory-content">Content</label>
      <textarea id="memory-content" v-model="suggestionContent" rows="4" placeholder="Add a durable fact or preference"></textarea>
      <label for="memory-importance">Importance</label>
      <input id="memory-importance" v-model.number="suggestionImportance" type="number" min="0" max="1" step="0.1" />
      <button type="button" :disabled="!conversationId || !suggestionContent.trim() || memory.writing" @click="writeMemory">
        {{ memory.writing ? 'Writing…' : 'Write memory' }}
      </button>
      <p v-if="memory.suggestion?.status === 'failed'" class="status-error">{{ memory.error || 'Memory write failed.' }}</p>
      <p v-else-if="memory.suggestion?.status === 'written'" class="status-success">Memory written.</p>
    </WorkbenchSection>

    <WorkbenchSection title="Context preview">
      <p class="muted">{{ previewQuery || 'Send a message to inspect its context.' }}</p>
      <button type="button" :disabled="!conversationId || !previewQuery || previewing" @click="previewContext">
        {{ previewing ? 'Loading…' : 'Preview context' }}
      </button>
      <dl v-if="memory.contextPreview" class="inspector-details">
        <div><dt>Tokens</dt><dd>{{ memory.contextPreview.estimatedTokens }} / {{ memory.contextPreview.tokenBudget }}</dd></div>
        <div><dt>Tiers</dt><dd>{{ (memory.contextPreview.includedTiers || []).join(', ') || 'None' }}</dd></div>
      </dl>
      <pre v-if="memory.contextPreview?.contextText">{{ memory.contextPreview.contextText }}</pre>
    </WorkbenchSection>

    <WorkbenchSection title="Diagnostics">
      <p v-if="!memory.diagnostics" class="muted">Preview context to load diagnostics.</p>
      <dl v-else class="inspector-details diagnostics-list">
        <template v-for="bucket in diagnosticBuckets" :key="bucket.label">
          <div v-if="bucket.value !== undefined && bucket.value !== null">
            <dt>{{ bucket.label }}</dt>
            <dd>{{ formatBucket(bucket.value) }}</dd>
          </div>
        </template>
      </dl>
    </WorkbenchSection>

    <WorkbenchSection title="Tools & permissions">
      <p class="muted">Runtime: {{ runtime.status }}</p>
      <p v-if="!runtime.tools.length" class="muted">No managed tools loaded.</p>
      <div v-else class="tool-list">
        <div v-for="tool in runtime.tools" :key="toolKey(tool)" class="tool-item">
          <strong>{{ toolLabel(tool) }}</strong>
          <PermissionNotice
            :risk="runtime.riskForTool(tool)"
            :reason="tool.description || tool.reason || ''"
            :confirmed="confirmedTools.includes(toolKey(tool))"
            @confirm="confirmTool(tool)"
          />
        </div>
      </div>
    </WorkbenchSection>

    <WorkbenchSection title="Recent calls">
      <p v-if="!workbench.recentInvocations.length" class="muted">No recent calls.</p>
      <ul v-else class="invocation-list">
        <li v-for="invocation in workbench.recentInvocations" :key="invocation.id">
          <strong>{{ invocation.name || invocation.operation || invocation.source }}</strong>
          <span>{{ invocation.status }} · {{ invocation.summary }}</span>
        </li>
      </ul>
    </WorkbenchSection>
  </aside>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import WorkbenchSection from './WorkbenchSection.vue'
import PermissionNotice from './PermissionNotice.vue'
import { useChatStore } from '../../stores/chat'
import { useMemoryStore } from '../../stores/memory'
import { useRuntimeStore } from '../../stores/runtime'
import { useWorkbenchStore } from '../../stores/workbench'

const chatStore = useChatStore()
const memory = useMemoryStore()
const runtime = useRuntimeStore()
const workbench = useWorkbenchStore()
const previewing = ref(false)
const confirmedTools = ref([])
const suggestionContent = ref('')
const suggestionImportance = ref(0.8)

const conversationId = computed(() => workbench.currentConversationId)
const chatMessages = computed(() => chatStore.assistantAppChats[conversationId.value]?.messages || [])
const lastUserMessage = computed(() => [...chatMessages.value].reverse().find((message) => message.isUser)?.content || '')
const previewQuery = computed(() => lastUserMessage.value.trim())
const activeMode = computed(() => workbench.currentMode)
const diagnosticBuckets = computed(() => {
  const diagnostics = memory.diagnostics || {}
  return [
    { label: 'Mode', value: diagnostics.mode },
    { label: 'Budget plan', value: diagnostics.budgetPlan },
    { label: 'History', value: diagnostics.history },
    { label: 'Memory', value: diagnostics.memoryContext },
    { label: 'Skills', value: diagnostics.skillContext },
    { label: 'Exposed tools', value: diagnostics.exposedTools }
  ]
})

watch(
  () => memory.suggestion,
  (suggestion) => {
    suggestionContent.value = suggestion?.content || ''
    suggestionImportance.value = suggestion?.importance ?? 0.8
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
  memory.updateSuggestion({
    content: suggestionContent.value.trim(),
    importance: suggestionImportance.value
  })
  try {
    await memory.writeSuggestion(conversationId.value)
  } catch {
    // The store exposes the write failure beside the editor.
  }
}

const previewContext = async () => {
  previewing.value = true
  try {
    await Promise.all([
      memory.previewContext(conversationId.value, previewQuery.value),
      memory.loadDiagnostics(conversationId.value, previewQuery.value)
    ])
  } finally {
    previewing.value = false
  }
}

const toolKey = (tool) => tool?.name || tool?.toolName || tool?.id || 'unknown-tool'
const toolLabel = (tool) => tool?.displayName || tool?.name || tool?.toolName || 'Unnamed tool'
const confirmTool = (tool) => {
  const key = toolKey(tool)
  if (!confirmedTools.value.includes(key)) confirmedTools.value = [...confirmedTools.value, key]
}
const formatBucket = (value) => typeof value === 'string'
  ? value
  : Array.isArray(value)
    ? value.join(', ') || 'None'
    : JSON.stringify(value)
</script>

<style scoped>
.inspector-panel { display: grid; align-content: start; gap: 12px; overflow-y: auto; }
.inspector-panel :deep(.workbench-section) { padding: 14px; box-shadow: none; }
.inspector-panel label { display: block; margin: 10px 0 4px; color: var(--color-text-muted); font-size: 0.75rem; font-weight: 700; }
.inspector-panel textarea, .inspector-panel input { width: 100%; padding: 7px 8px; color: var(--color-text); background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius-sm); resize: vertical; }
.inspector-panel button { width: 100%; margin-top: 10px; padding: 6px 10px; color: #0f1115; background: var(--color-primary); border: 0; border-radius: var(--radius-sm); }
.inspector-panel button:disabled { color: var(--color-text-subtle); background: var(--color-panel-muted); cursor: not-allowed; }
.inspector-details { display: grid; gap: 6px; margin: 0; font-size: 0.78rem; }
.inspector-details div { display: grid; grid-template-columns: 88px minmax(0, 1fr); gap: 8px; }
.inspector-details dt { color: var(--color-text-subtle); }
.inspector-details dd { min-width: 0; margin: 0; overflow-wrap: anywhere; }
.diagnostics-list dd { white-space: pre-wrap; }
.status-error { margin: 8px 0 0; color: var(--color-danger); font-size: 0.78rem; }
.status-success { margin: 8px 0 0; color: var(--color-success); font-size: 0.78rem; }
pre { max-height: 180px; margin: 10px 0 0; padding: 8px; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; color: var(--color-text-muted); background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius-sm); font: inherit; font-size: 0.75rem; }
.tool-list { display: grid; gap: 8px; }
.tool-item { display: grid; gap: 5px; font-size: 0.78rem; }
.invocation-list { display: grid; gap: 8px; margin: 0; padding: 0; list-style: none; }
.invocation-list li { display: grid; gap: 2px; font-size: 0.78rem; }
.invocation-list span { color: var(--color-text-muted); overflow-wrap: anywhere; }

@media (max-width: 900px) {
  .inspector-panel { max-height: 52vh; }
}
</style>
