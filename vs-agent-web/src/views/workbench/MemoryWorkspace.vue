<template>
  <section class="workspace">
    <header class="workspace-header"><div><h1>记忆</h1><p class="muted">管理可长期保留的对话记忆，并查看它为当前对话提供的上下文。</p></div><button type="button" :disabled="!conversationId || memory.loading" @click="loadConversation">{{ memory.loading ? '加载中…' : '重新加载记忆' }}</button></header>
    <p v-if="!conversationId" class="status-warning">请先打开或新建一个会话，以建立对话上下文。</p><p v-if="memory.error || localError" class="status-error">{{ localError || memory.error }}</p>
    <WorkbenchSection title="记忆如何工作" class="guide-panel"><ol><li><strong>自动：</strong>在对话中说“请记住：我偏好中文回答，先给结论再解释”。对话完成后，系统会提取稳定偏好并自动写入。</li><li><strong>手动：</strong>在下方填写需要长期保留的事实或偏好并保存。</li><li><strong>安全：</strong>密码、身份证、银行卡等敏感信息不会写入长期记忆。</li></ol><button type="button" @click="tryMemoryExample">体验自动写入示例</button></WorkbenchSection>
    <div class="workspace-grid">
      <div class="stack">
        <WorkbenchSection title="手动写入语义记忆" class="panel"><label for="manual-memory">长期事实或偏好</label><textarea id="manual-memory" v-model="manualContent" rows="5" placeholder="输入需要长期保留的事实或偏好" /><label for="manual-importance">重要程度（0–1）</label><input id="manual-importance" v-model.number="manualImportance" type="number" min="0" max="1" step="0.1" /><button type="button" :disabled="!conversationId || !manualContent.trim() || memory.writing" @click="writeManual">{{ memory.writing ? '写入中…' : '写入语义记忆' }}</button></WorkbenchSection>
        <WorkbenchSection v-if="memory.suggestion" title="推荐写入的记忆" class="panel"><textarea v-model="suggestionContent" rows="4" /><input v-model.number="suggestionImportance" type="number" min="0" max="1" step="0.1" /><button type="button" :disabled="!conversationId || !suggestionContent.trim() || memory.writing" @click="writeSuggestion">{{ memory.writing ? '写入中…' : '写入推荐记忆' }}</button><p v-if="memory.suggestion.status === 'written'" class="status-success">推荐记忆已写入。</p></WorkbenchSection>
        <WorkbenchSection title="清空对话记忆" class="panel danger-panel"><label for="clear-confirmation">输入 CLEAR 以启用此操作</label><input id="clear-confirmation" v-model="clearConfirmation" autocomplete="off" placeholder="CLEAR" /><button type="button" :disabled="!conversationId || clearConfirmation !== 'CLEAR'" @click="clearMemory">清空记忆</button></WorkbenchSection>
      </div>
      <div class="stack">
        <WorkbenchSection title="对话记忆" class="panel"><p v-if="!memory.conversation" class="muted">当前会话尚未加载记忆。</p><template v-else><dl class="memory-stats"><div><dt>工作消息</dt><dd>{{ memory.conversation.workingMessageCount ?? 0 }}</dd></div><div><dt>语义记忆</dt><dd>{{ memory.conversation.semanticMemories?.length ?? 0 }}</dd></div><div><dt>情节记忆</dt><dd>{{ memory.conversation.episodicMemories?.length ?? 0 }}</dd></div></dl><h3>滚动摘要</h3><pre>{{ memory.conversation.rollingSummary || '暂无滚动摘要。' }}</pre><h3>语义记忆</h3><ul><li v-for="item in memory.conversation.semanticMemories || []" :key="item.id || item.content">{{ item.content }}</li><li v-if="!(memory.conversation.semanticMemories || []).length" class="muted">暂无语义记忆。</li></ul></template></WorkbenchSection>
        <WorkbenchSection title="上下文预览" class="panel"><input v-model="previewQuery" placeholder="输入要预览的问题或任务" /><button type="button" :disabled="!conversationId || !previewQuery.trim()" @click="previewContext">预览上下文</button><pre v-if="memory.contextPreview">{{ pretty(memory.contextPreview) }}</pre></WorkbenchSection>
        <WorkbenchSection title="诊断" class="panel"><input v-model="diagnosticQuery" placeholder="输入要诊断的问题或任务" /><select v-model="diagnosticMode"><option value="plain">普通对话</option><option value="skills">技能</option><option value="mcp">MCP</option></select><button type="button" :disabled="!conversationId || !diagnosticQuery.trim()" @click="loadDiagnostics">加载诊断</button><pre v-if="memory.diagnostics">{{ pretty(memory.diagnostics) }}</pre></WorkbenchSection>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import WorkbenchSection from '../../components/workbench/WorkbenchSection.vue'
import { useMemoryStore } from '../../stores/memory'
import { useWorkbenchStore } from '../../stores/workbench'
import { useRouter } from 'vue-router'

const memory = useMemoryStore()
const workbench = useWorkbenchStore()
const router = useRouter()
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
const tryMemoryExample = () => router.push({ path: '/', query: { example: 'memory' } })
const loadConversation = async () => { localError.value = ''; if (conversationId.value) await memory.loadConversation(conversationId.value) }
const writeManual = async () => { localError.value = ''; try { await memory.writeManual(conversationId.value, { content: manualContent.value, importance: manualImportance.value }); manualContent.value = ''; workbench.addInvocation({ source: 'memory', name: '写入语义记忆', status: 'complete', summary: '已写入手动语义记忆。' }) } catch { localError.value = memory.error || '记忆写入失败。' } }
const writeSuggestion = async () => { localError.value = ''; memory.updateSuggestion({ content: suggestionContent.value, importance: suggestionImportance.value }); try { await memory.writeSuggestion(conversationId.value); workbench.addInvocation({ source: 'memory', name: '写入推荐记忆', status: 'complete', summary: '已写入推荐记忆。' }) } catch { localError.value = memory.error || '推荐记忆写入失败。' } }
const previewContext = async () => { localError.value = ''; try { await memory.previewContext(conversationId.value, previewQuery.value.trim()) } catch { localError.value = memory.error || '上下文预览失败。' } }
const loadDiagnostics = async () => { localError.value = ''; try { await memory.loadDiagnostics(conversationId.value, diagnosticQuery.value.trim(), diagnosticMode.value) } catch { localError.value = memory.error || '诊断加载失败。' } }
const clearMemory = async () => { if (clearConfirmation.value !== 'CLEAR') return; localError.value = ''; try { await memory.clearConversation(conversationId.value); clearConfirmation.value = ''; workbench.addInvocation({ source: 'memory', name: '清空对话记忆', status: 'complete', summary: '已清空对话记忆。' }) } catch { localError.value = memory.error || '清空记忆失败。' } }
watch(conversationId, () => loadConversation(), { immediate: true })
watch(() => memory.suggestion, (suggestion) => { suggestionContent.value = suggestion?.content || ''; suggestionImportance.value = suggestion?.importance ?? 0.8 }, { immediate: true, deep: true })
</script>

<style scoped>
.workspace { display: grid; gap: 16px; padding: 20px; }.workspace-header { display: flex; align-items: center; justify-content: space-between; gap: 14px; }.workspace-header h1 { margin: 0 0 4px; font-size: 1.2rem; }.workspace-header p { margin: 0; }.guide-panel { display: grid; gap: 10px; }.guide-panel ol { display: grid; gap: 8px; margin: 0; padding-left: 20px; color: var(--color-text-muted); line-height: 1.6; }.guide-panel strong { color: var(--color-text); }.guide-panel button { justify-self: start; }.workspace-grid { display: grid; grid-template-columns: minmax(250px, .8fr) minmax(0, 1.2fr); gap: 16px; }.stack, .panel { display: grid; align-content: start; gap: 10px; }.stack { gap: 16px; }.panel label, .panel h3 { color: var(--color-text-muted); font-size: .8rem; }.panel h3 { margin: 4px 0 0; }.panel textarea, .panel input, .panel select { width: 100%; padding: 9px; color: var(--color-text); background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }.danger-panel button { background: var(--color-danger); }.memory-stats { display: grid; gap: 6px; margin: 0; }.memory-stats div { display: flex; justify-content: space-between; gap: 10px; }.memory-stats dt { color: var(--color-text-muted); }.memory-stats dd { margin: 0; }.panel pre { max-height: 260px; margin: 0; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; padding: 10px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius-sm); font-size: .78rem; }.panel ul { display: grid; gap: 7px; margin: 0; padding-left: 20px; }.status-error { margin: 0; color: var(--color-danger); }.status-warning { margin: 0; color: var(--color-warning); }.status-success { margin: 0; color: var(--color-success); }button { cursor: pointer; }button:disabled { opacity: .55; cursor: not-allowed; }@media (max-width: 800px) { .workspace { padding: 14px; }.workspace-grid { grid-template-columns: 1fr; }.workspace-header { align-items: flex-start; flex-direction: column; } }
</style>
