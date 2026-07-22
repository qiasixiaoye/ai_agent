<template>
  <section class="workspace">
    <header class="workspace-header"><div><h1>Runtime</h1><p class="muted">Managed MCP health, catalog state, and controlled invocation.</p></div><button type="button" :disabled="runtime.loading" @click="refresh">{{ runtime.loading ? 'Refreshing...' : 'Refresh runtime' }}</button></header>
    <p v-if="runtime.error || localError" class="status-error">{{ localError || runtime.error }}</p>
    <div class="workspace-grid">
      <WorkbenchSection title="Runtime health" class="panel"><dl class="health-list"><div><dt>Status</dt><dd :class="runtime.status">{{ runtime.status }}</dd></div><div><dt>Catalog</dt><dd>{{ runtime.tools.length }} managed tools</dd></div><div v-if="runtime.health?.providerAvailable !== undefined"><dt>Provider</dt><dd>{{ runtime.health.providerAvailable ? 'available' : 'unavailable' }}</dd></div><div v-if="runtime.health?.openCircuits !== undefined"><dt>Open circuits</dt><dd>{{ runtime.health.openCircuits }}</dd></div></dl><p class="muted">Errors may include provider timeout or circuit-breaker information returned by the runtime.</p></WorkbenchSection>
      <WorkbenchSection title="Managed invocation" class="panel invocation-panel"><p v-if="!runtime.tools.length" class="muted">No managed tools are currently available.</p><template v-else><label for="runtime-tool">Tool</label><select id="runtime-tool" v-model="selectedName" @change="resetInvocation"><option v-for="tool in runtime.tools" :key="toolKey(tool)" :value="toolKey(tool)">{{ toolLabel(tool) }}</option></select><p class="muted">{{ selectedTool?.description || 'No description supplied.' }}</p><PermissionNotice :risk="risk" :reason="selectedTool?.reason || selectedTool?.description || ''" :confirmed="confirmed" @confirm="confirmAndInvoke" /><label for="runtime-arguments">Arguments (JSON)</label><textarea id="runtime-arguments" v-model="argumentsJson" rows="8" spellcheck="false" /><p v-if="jsonError" class="status-error">{{ jsonError }}</p><button v-if="risk === 'safe'" type="button" :disabled="runtime.invoking || selectedTool?.enabled === false" @click="invoke(false)">{{ runtime.invoking ? 'Invoking...' : 'Invoke managed tool' }}</button><button v-else-if="risk !== 'blocked'" type="button" :disabled="runtime.invoking || selectedTool?.enabled === false" @click="confirmAndInvoke">{{ runtime.invoking ? 'Invoking...' : 'Confirm and invoke' }}</button><p v-else class="status-error">This tool is blocked by runtime policy.</p></template></WorkbenchSection>
    </div>
    <WorkbenchSection v-if="runtime.lastResult" title="Last invocation result" class="panel result-panel"><pre>{{ pretty(runtime.lastResult) }}</pre></WorkbenchSection>
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
const selectedName = ref('')
const argumentsJson = ref('{}')
const confirmed = ref(false)
const jsonError = ref('')
const localError = ref('')
const toolKey = (tool) => tool?.name || tool?.toolName || tool?.id || ''
const toolLabel = (tool) => tool?.displayName || toolKey(tool) || 'Unnamed tool'
const selectedTool = computed(() => runtime.tools.find((tool) => toolKey(tool) === selectedName.value) || null)
const risk = computed(() => runtime.riskForTool(selectedTool.value))
const pretty = (value) => JSON.stringify(value, null, 2)
const invocationSummary = (result) => result?.success === false
  ? result.errorMessage || result.message || 'Managed MCP invocation failed.'
  : 'Managed runtime invocation completed.'
const resetInvocation = () => { argumentsJson.value = '{}'; confirmed.value = false; jsonError.value = ''; localError.value = '' }
const initializeSelection = () => { if (!runtime.tools.some((tool) => toolKey(tool) === selectedName.value)) selectedName.value = toolKey(runtime.tools[0]); resetInvocation() }
const refresh = async () => { localError.value = ''; await Promise.all([runtime.loadHealth(), runtime.refreshTools()]); if (runtime.tools.length) initializeSelection() }
const invoke = async (isConfirmed) => {
  jsonError.value = ''; localError.value = ''
  let normalized
  try { normalized = runtime.normalizeArgumentsJson(argumentsJson.value) } catch { jsonError.value = 'Arguments must be valid JSON before the tool can run.'; return }
  try {
    const result = await runtime.invokeTool({ toolName: selectedName.value, argumentsJson: normalized, confirmed: isConfirmed })
    workbench.addInvocation({
      source: 'runtime',
      name: toolLabel(selectedTool.value),
      status: result?.success === false ? 'failed' : 'complete',
      summary: invocationSummary(result)
    })
  } catch { localError.value = runtime.error || 'Managed tool invocation failed.' }
}
const confirmAndInvoke = async () => { confirmed.value = true; await invoke(true) }
onMounted(async () => { await Promise.all([runtime.loadHealth(), runtime.loadTools()]); if (runtime.tools.length) initializeSelection() })
</script>

<style scoped>
.workspace { display: grid; gap: 16px; padding: 20px; }.workspace-header { display: flex; align-items: center; justify-content: space-between; gap: 14px; }.workspace-header h1 { margin: 0 0 4px; font-size: 1.2rem; }.workspace-header p { margin: 0; }.workspace-grid { display: grid; grid-template-columns: minmax(220px, .7fr) minmax(0, 1.3fr); gap: 16px; }.panel { display: grid; align-content: start; gap: 10px; }.health-list { display: grid; gap: 8px; margin: 0; }.health-list div { display: flex; justify-content: space-between; gap: 10px; }.health-list dt { color: var(--color-text-muted); }.health-list dd { margin: 0; }.health-list dd.healthy, .health-list dd.up { color: var(--color-success); }.health-list dd.error, .health-list dd.down { color: var(--color-danger); }.invocation-panel label { color: var(--color-text-muted); font-size: .8rem; }.invocation-panel select, textarea { width: 100%; padding: 9px; color: var(--color-text); background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }.status-error { margin: 0; color: var(--color-danger); }.result-panel pre { max-height: 340px; margin: 0; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; padding: 10px; color: var(--color-text); background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius-sm); font-size: .78rem; }button { cursor: pointer; }button:disabled { opacity: .55; cursor: not-allowed; }@media (max-width: 800px) { .workspace { padding: 14px; }.workspace-grid { grid-template-columns: 1fr; }.workspace-header { align-items: flex-start; flex-direction: column; } }
</style>
