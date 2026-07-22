<template>
  <section class="workspace">
    <header class="workspace-header">
      <div><h1>Tools</h1><p class="muted">Run managed MCP tools with client-side argument validation.</p></div>
      <button type="button" :disabled="runtime.loading" @click="loadTools">{{ runtime.loading ? 'Loading...' : 'Reload catalog' }}</button>
    </header>

    <p v-if="runtime.error || localError" class="status-error">{{ localError || runtime.error }}</p>
    <div class="workspace-grid">
      <WorkbenchSection title="Catalog" class="catalog-panel">
        <input v-model="search" class="search" type="search" placeholder="Search tools" />
        <p v-if="!filteredTools.length" class="muted">No matching managed tools.</p>
        <button v-for="tool in filteredTools" :key="toolKey(tool)" type="button" class="catalog-item" :class="{ active: toolKey(tool) === toolKey(selectedTool) }" @click="selectTool(tool)">
          <strong>{{ toolLabel(tool) }}</strong><span>{{ tool.riskLevel || tool.risk || 'unknown' }}</span>
        </button>
      </WorkbenchSection>

      <WorkbenchSection title="Invocation" class="detail-panel">
        <p v-if="!selectedTool" class="muted">Choose a tool to inspect its metadata and invoke it.</p>
        <template v-else>
          <div class="tool-metadata"><strong>{{ toolLabel(selectedTool) }}</strong><span>{{ selectedTool.description || 'No description supplied.' }}</span><span>State: {{ selectedTool.circuitState || 'unknown' }}</span></div>
          <PermissionNotice :risk="risk" :reason="selectedTool.reason || selectedTool.description || ''" :confirmed="confirmed" @confirm="confirmAndInvoke" />
          <label for="tool-arguments">Arguments (JSON)</label>
          <textarea id="tool-arguments" v-model="argumentsJson" rows="11" spellcheck="false" />
          <p v-if="jsonError" class="status-error">{{ jsonError }}</p>
          <button v-if="risk === 'safe'" type="button" :disabled="runtime.invoking" @click="invoke(false)">{{ runtime.invoking ? 'Invoking...' : 'Run tool' }}</button>
          <button v-else-if="risk !== 'blocked'" type="button" :disabled="runtime.invoking" @click="confirmAndInvoke">{{ runtime.invoking ? 'Invoking...' : 'Confirm and run' }}</button>
          <p v-else class="status-error">This tool is blocked by its runtime policy.</p>
          <pre v-if="runtime.lastResult">{{ pretty(runtime.lastResult) }}</pre>
        </template>
      </WorkbenchSection>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import PermissionNotice from '../../components/workbench/PermissionNotice.vue'
import WorkbenchSection from '../../components/workbench/WorkbenchSection.vue'
import { useRuntimeStore } from '../../stores/runtime'
import { useWorkbenchStore } from '../../stores/workbench'

const runtime = useRuntimeStore()
const workbench = useWorkbenchStore()
const search = ref('')
const selectedTool = ref(null)
const argumentsJson = ref('{}')
const confirmed = ref(false)
const jsonError = ref('')
const localError = ref('')
const toolKey = (tool) => tool?.name || tool?.toolName || tool?.id || ''
const toolLabel = (tool) => tool?.displayName || toolKey(tool) || 'Unnamed tool'
const risk = computed(() => runtime.riskForTool(selectedTool.value))
const filteredTools = computed(() => {
  const query = search.value.trim().toLowerCase()
  return runtime.tools.filter((tool) => !query || `${toolLabel(tool)} ${tool.description || ''}`.toLowerCase().includes(query))
})
const pretty = (value) => JSON.stringify(value, null, 2)
const invocationSummary = (result) => result?.success === false
  ? result.errorMessage || result.message || 'Managed MCP invocation failed.'
  : 'Managed MCP invocation completed.'

const selectTool = (tool) => { selectedTool.value = tool; argumentsJson.value = '{}'; confirmed.value = false; jsonError.value = ''; localError.value = '' }
const loadTools = async () => { await runtime.loadTools(); if (!selectedTool.value && runtime.tools.length) selectTool(runtime.tools[0]) }
const invoke = async (isConfirmed) => {
  jsonError.value = ''; localError.value = ''
  let normalized
  try { normalized = runtime.normalizeArgumentsJson(argumentsJson.value) } catch { jsonError.value = 'Arguments must be valid JSON before the tool can run.'; return }
  try {
    const result = await runtime.invokeTool({ toolName: toolKey(selectedTool.value), argumentsJson: normalized, confirmed: isConfirmed })
    workbench.addInvocation({
      source: 'tools',
      name: toolLabel(selectedTool.value),
      status: result?.success === false ? 'failed' : 'complete',
      summary: invocationSummary(result)
    })
    return result
  } catch { localError.value = runtime.error || 'Tool invocation failed.' }
}
const confirmAndInvoke = async () => { confirmed.value = true; await invoke(true) }
onMounted(loadTools)
</script>

<style scoped>
.workspace { display: grid; gap: 16px; padding: 20px; }.workspace-header { display: flex; align-items: center; justify-content: space-between; gap: 14px; }.workspace-header h1 { margin: 0 0 4px; font-size: 1.2rem; }.workspace-header p { margin: 0; }.workspace-grid { display: grid; grid-template-columns: minmax(220px, .75fr) minmax(0, 1.5fr); gap: 16px; }.catalog-panel, .detail-panel { display: grid; align-content: start; gap: 10px; }.search, textarea { width: 100%; color: var(--color-text); background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius-sm); padding: 9px; }.catalog-item { display: grid; gap: 3px; text-align: left; color: var(--color-text); background: var(--color-panel-muted); border: 1px solid var(--color-border); border-radius: var(--radius-sm); padding: 10px; }.catalog-item.active { border-color: var(--color-primary); }.catalog-item span, .tool-metadata span { color: var(--color-text-muted); font-size: .8rem; }.tool-metadata { display: grid; gap: 5px; }.detail-panel label { color: var(--color-text-muted); font-size: .8rem; }.status-error { margin: 0; color: var(--color-danger); }.detail-panel pre { max-height: 300px; margin: 0; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; padding: 10px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius-sm); font-size: .78rem; }button { cursor: pointer; }button:disabled { opacity: .55; cursor: not-allowed; }@media (max-width: 800px) { .workspace { padding: 14px; }.workspace-grid { grid-template-columns: 1fr; }.workspace-header { align-items: flex-start; flex-direction: column; } }
</style>
