<template>
  <section class="workspace">
    <header class="workspace-header"><div><h1>Memory</h1><p class="muted">Manage durable conversation memory and inspect the context it supplies.</p></div><button type="button" :disabled="!conversationId || memory.loading" @click="loadConversation">{{ memory.loading ? 'Loading...' : 'Reload memory' }}</button></header>
    <p v-if="!conversationId" class="status-warning">Open or start a chat first to create a conversation context.</p><p v-if="memory.error || localError" class="status-error">{{ localError || memory.error }}</p>
    <div class="workspace-grid">
      <div class="stack">
        <WorkbenchSection title="Manual semantic memory" class="panel"><label for="manual-memory">Durable fact or preference</label><textarea id="manual-memory" v-model="manualContent" rows="5" placeholder="Add a durable fact or preference" /><label for="manual-importance">Importance (0–1)</label><input id="manual-importance" v-model.number="manualImportance" type="number" min="0" max="1" step="0.1" /><button type="button" :disabled="!conversationId || !manualContent.trim() || memory.writing" @click="writeManual">{{ memory.writing ? 'Writing...' : 'Write semantic memory' }}</button></WorkbenchSection>
        <WorkbenchSection v-if="memory.suggestion" title="Suggested memory" class="panel"><textarea v-model="suggestionContent" rows="4" /><input v-model.number="suggestionImportance" type="number" min="0" max="1" step="0.1" /><button type="button" :disabled="!conversationId || !suggestionContent.trim() || memory.writing" @click="writeSuggestion">{{ memory.writing ? 'Writing...' : 'Write suggestion' }}</button><p v-if="memory.suggestion.status === 'written'" class="status-success">Suggestion written.</p></WorkbenchSection>
        <WorkbenchSection title="Clear conversation memory" class="panel danger-panel"><label for="clear-confirmation">Type CLEAR to enable this action</label><input id="clear-confirmation" v-model="clearConfirmation" autocomplete="off" placeholder="CLEAR" /><button type="button" :disabled="!conversationId || clearConfirmation !== 'CLEAR'" @click="clearMemory">Clear memory</button></WorkbenchSection>
      </div>
      <div class="stack">
        <WorkbenchSection title="Conversation memory" class="panel"><p v-if="!memory.conversation" class="muted">No memory loaded for this conversation.</p><template v-else><dl class="memory-stats"><div><dt>Working messages</dt><dd>{{ memory.conversation.workingMessageCount ?? 0 }}</dd></div><div><dt>Semantic memories</dt><dd>{{ memory.conversation.semanticMemories?.length ?? 0 }}</dd></div><div><dt>Episodic memories</dt><dd>{{ memory.conversation.episodicMemories?.length ?? 0 }}</dd></div></dl><h3>Rolling summary</h3><pre>{{ memory.conversation.rollingSummary || 'No rolling summary yet.' }}</pre><h3>Semantic memories</h3><ul><li v-for="item in memory.conversation.semanticMemories || []" :key="item.id || item.content">{{ item.content }}</li><li v-if="!(memory.conversation.semanticMemories || []).length" class="muted">No semantic memories yet.</li></ul></template></WorkbenchSection>
        <WorkbenchSection title="Context preview" class="panel"><input v-model="previewQuery" placeholder="Question or task to preview" /><button type="button" :disabled="!conversationId || !previewQuery.trim()" @click="previewContext">Preview context</button><pre v-if="memory.contextPreview">{{ pretty(memory.contextPreview) }}</pre></WorkbenchSection>
        <WorkbenchSection title="Diagnostics" class="panel"><input v-model="diagnosticQuery" placeholder="Question or task to diagnose" /><select v-model="diagnosticMode"><option value="plain">Plain chat</option><option value="skills">Skills</option><option value="mcp">MCP</option></select><button type="button" :disabled="!conversationId || !diagnosticQuery.trim()" @click="loadDiagnostics">Load diagnostics</button><pre v-if="memory.diagnostics">{{ pretty(memory.diagnostics) }}</pre></WorkbenchSection>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import WorkbenchSection from '../../components/workbench/WorkbenchSection.vue'
import { useMemoryStore } from '../../stores/memory'
import { useWorkbenchStore } from '../../stores/workbench'

const memory = useMemoryStore()
const workbench = useWorkbenchStore()
const manualContent = ref('')
const manualImportance = ref(0.8)
const suggestionContent = ref('')
const suggestionImportance = ref(0.8)
const previewQuery = ref('')
const diagnosticQuery = ref('')
const diagnosticMode = ref('plain')
const clearConfirmation = ref('')
const localError = ref('')
const conversationId = computed(() => workbench.currentConversationId)
const pretty = (value) => JSON.stringify(value, null, 2)
const loadConversation = async () => { localError.value = ''; if (conversationId.value) await memory.loadConversation(conversationId.value) }
const writeManual = async () => { localError.value = ''; try { await memory.writeManual(conversationId.value, { content: manualContent.value, importance: manualImportance.value }); manualContent.value = ''; workbench.addInvocation({ source: 'memory', name: 'write-semantic-memory', status: 'complete', summary: 'Manual semantic memory written.' }) } catch { localError.value = memory.error || 'Memory write failed.' } }
const writeSuggestion = async () => { localError.value = ''; memory.updateSuggestion({ content: suggestionContent.value, importance: suggestionImportance.value }); try { await memory.writeSuggestion(conversationId.value); workbench.addInvocation({ source: 'memory', name: 'write-memory-suggestion', status: 'complete', summary: 'Suggested memory written.' }) } catch { localError.value = memory.error || 'Suggested memory write failed.' } }
const previewContext = async () => { localError.value = ''; try { await memory.previewContext(conversationId.value, previewQuery.value.trim()) } catch { localError.value = memory.error || 'Context preview failed.' } }
const loadDiagnostics = async () => { localError.value = ''; try { await memory.loadDiagnostics(conversationId.value, diagnosticQuery.value.trim(), diagnosticMode.value) } catch { localError.value = memory.error || 'Diagnostics failed.' } }
const clearMemory = async () => { if (clearConfirmation.value !== 'CLEAR') return; localError.value = ''; try { await memory.clearConversation(conversationId.value); clearConfirmation.value = ''; workbench.addInvocation({ source: 'memory', name: 'clear-conversation-memory', status: 'complete', summary: 'Conversation memory cleared.' }) } catch { localError.value = memory.error || 'Memory clear failed.' } }
watch(conversationId, () => loadConversation(), { immediate: true })
watch(() => memory.suggestion, (suggestion) => { suggestionContent.value = suggestion?.content || ''; suggestionImportance.value = suggestion?.importance ?? 0.8 }, { immediate: true, deep: true })
</script>

<style scoped>
.workspace { display: grid; gap: 16px; padding: 20px; }.workspace-header { display: flex; align-items: center; justify-content: space-between; gap: 14px; }.workspace-header h1 { margin: 0 0 4px; font-size: 1.2rem; }.workspace-header p { margin: 0; }.workspace-grid { display: grid; grid-template-columns: minmax(250px, .8fr) minmax(0, 1.2fr); gap: 16px; }.stack, .panel { display: grid; align-content: start; gap: 10px; }.stack { gap: 16px; }.panel label, .panel h3 { color: var(--color-text-muted); font-size: .8rem; }.panel h3 { margin: 4px 0 0; }.panel textarea, .panel input, .panel select { width: 100%; padding: 9px; color: var(--color-text); background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }.danger-panel button { background: var(--color-danger); }.memory-stats { display: grid; gap: 6px; margin: 0; }.memory-stats div { display: flex; justify-content: space-between; gap: 10px; }.memory-stats dt { color: var(--color-text-muted); }.memory-stats dd { margin: 0; }.panel pre { max-height: 260px; margin: 0; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; padding: 10px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius-sm); font-size: .78rem; }.panel ul { display: grid; gap: 7px; margin: 0; padding-left: 20px; }.status-error { margin: 0; color: var(--color-danger); }.status-warning { margin: 0; color: var(--color-warning); }.status-success { margin: 0; color: var(--color-success); }button { cursor: pointer; }button:disabled { opacity: .55; cursor: not-allowed; }@media (max-width: 800px) { .workspace { padding: 14px; }.workspace-grid { grid-template-columns: 1fr; }.workspace-header { align-items: flex-start; flex-direction: column; } }
</style>
