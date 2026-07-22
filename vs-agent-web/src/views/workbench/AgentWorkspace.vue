<template>
  <section class="workspace">
    <header class="workspace-header">
      <div>
        <h1>Agent platform</h1>
        <p class="muted">Run registered capabilities, direct task definitions, and the included demonstrations.</p>
      </div>
      <button type="button" :disabled="catalogLoading" @click="refreshTools">{{ catalogLoading ? 'Refreshing...' : 'Refresh catalog' }}</button>
    </header>

    <p v-if="localError" class="status-error">{{ localError }}</p>

    <div class="workspace-grid">
      <WorkbenchSection title="Capability catalog" class="panel">
        <p v-if="!catalogLoading && !tools.length" class="muted">No platform tools are available from the backend.</p>
        <label for="platform-tool">Tool</label>
        <select id="platform-tool" v-model="selectedToolName" :disabled="!tools.length || toolRunning">
          <option v-for="tool in tools" :key="toolKey(tool)" :value="toolKey(tool)">{{ toolLabel(tool) }}</option>
        </select>
        <p v-if="selectedTool" class="muted">{{ selectedTool.description || 'No description supplied.' }}</p>
        <p v-if="selectedTool?.requiredParams?.length" class="muted">Required: {{ selectedTool.requiredParams.join(', ') }}</p>
        <label for="platform-arguments">Arguments (JSON object)</label>
        <textarea id="platform-arguments" v-model="toolArgsJson" rows="7" spellcheck="false" />
        <p v-if="toolJsonError" class="status-error">{{ toolJsonError }}</p>
        <button type="button" :disabled="!selectedToolName || toolRunning" @click="runTool">{{ toolRunning ? 'Running...' : 'Run tool' }}</button>
        <pre v-if="toolResult">{{ pretty(toolResult) }}</pre>
      </WorkbenchSection>

      <div class="stack">
        <WorkbenchSection title="Direct task execution" class="panel">
          <p class="muted">Paste a task object with its steps and tool arguments.</p>
          <textarea v-model="taskJson" rows="10" spellcheck="false" aria-label="Task JSON" />
          <p v-if="taskJsonError" class="status-error">{{ taskJsonError }}</p>
          <button type="button" :disabled="taskRunning" @click="runTask">{{ taskRunning ? 'Running...' : 'Run task JSON' }}</button>
          <pre v-if="taskResult">{{ pretty(taskResult) }}</pre>
        </WorkbenchSection>

        <WorkbenchSection title="Demo task" class="panel">
          <label for="demo-query">Query</label>
          <input id="demo-query" v-model="demoQuery" placeholder="Plan a useful research task" @keyup.enter="runDemoTask" />
          <button type="button" :disabled="demoRunning || !demoQuery.trim()" @click="runDemoTask">{{ demoRunning ? 'Running...' : 'Run demo task' }}</button>
          <pre v-if="demoResult">{{ pretty(demoResult) }}</pre>
        </WorkbenchSection>
      </div>
    </div>

    <WorkbenchSection title="Astro demonstration" class="panel astro-panel">
      <p class="muted">Runs the platform's multi-step astronomy planning workflow.</p>
      <div class="astro-form">
        <label>Latitude <input v-model.number="astro.latitude" type="number" step="0.0001" /></label>
        <label>Longitude <input v-model.number="astro.longitude" type="number" step="0.0001" /></label>
        <label>Date <input v-model="astro.date" type="date" /></label>
        <button type="button" :disabled="astroRunning" @click="runAstro">{{ astroRunning ? 'Running...' : 'Run astro demo' }}</button>
      </div>
      <pre v-if="astroResult">{{ pretty(astroResult) }}</pre>
    </WorkbenchSection>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import WorkbenchSection from '../../components/workbench/WorkbenchSection.vue'
import {
  executePlatformDemoTask,
  executePlatformTask,
  executePlatformTool,
  listPlatformTools,
  runAstroDemo
} from '../../services/api'
import { useWorkbenchStore } from '../../stores/workbench'
import { parseJsonObject } from '../../utils/streamLifecycle'

const workbench = useWorkbenchStore()
const tools = ref([])
const catalogLoading = ref(false)
const selectedToolName = ref('')
const toolArgsJson = ref('{}')
const toolJsonError = ref('')
const toolResult = ref(null)
const toolRunning = ref(false)
const taskJson = ref(JSON.stringify({ maxSteps: 1, steps: [] }, null, 2))
const taskJsonError = ref('')
const taskResult = ref(null)
const taskRunning = ref(false)
const demoQuery = ref('Plan a short research task.')
const demoResult = ref(null)
const demoRunning = ref(false)
const astroResult = ref(null)
const astroRunning = ref(false)
const localError = ref('')
const astro = reactive({ latitude: 39.9042, longitude: 116.4074, date: new Date().toISOString().slice(0, 10) })

const toolKey = (tool) => tool?.toolName || tool?.name || tool?.id || ''
const toolLabel = (tool) => tool?.displayName || toolKey(tool) || 'Unnamed tool'
const selectedTool = computed(() => tools.value.find((tool) => toolKey(tool) === selectedToolName.value) || null)
const pretty = (value) => JSON.stringify(value, null, 2)
const invocationStatus = (result) => result?.success === false ? 'failed' : 'complete'
const resultSummary = (result, fallback) => result?.errorMessage || result?.message || result?.summary || fallback
const record = (name, result, fallback) => workbench.addInvocation({ source: 'agent-platform', name, status: invocationStatus(result), summary: resultSummary(result, fallback) })

const refreshTools = async () => {
  catalogLoading.value = true
  localError.value = ''
  try {
    tools.value = await listPlatformTools()
    if (!tools.value.some((tool) => toolKey(tool) === selectedToolName.value)) selectedToolName.value = toolKey(tools.value[0])
  } catch (error) {
    localError.value = error.message || 'Unable to load the platform tool catalog.'
  } finally {
    catalogLoading.value = false
  }
}

const runTool = async () => {
  toolJsonError.value = ''
  localError.value = ''
  try {
    const args = parseJsonObject(toolArgsJson.value)
    toolRunning.value = true
    toolResult.value = await executePlatformTool(selectedToolName.value, args)
    record(toolLabel(selectedTool.value), toolResult.value, 'Platform tool completed.')
  } catch (error) {
    if (error.message?.startsWith('Arguments')) toolJsonError.value = error.message
    else localError.value = error.message || 'Platform tool execution failed.'
  } finally {
    toolRunning.value = false
  }
}

const runTask = async () => {
  taskJsonError.value = ''
  localError.value = ''
  try {
    const task = parseJsonObject(taskJson.value, 'Task')
    taskRunning.value = true
    taskResult.value = await executePlatformTask(task)
    record('execute-task', taskResult.value, 'Direct platform task completed.')
  } catch (error) {
    if (error.message?.startsWith('Task')) taskJsonError.value = error.message
    else localError.value = error.message || 'Direct task execution failed.'
  } finally {
    taskRunning.value = false
  }
}

const runDemoTask = async () => {
  localError.value = ''
  demoRunning.value = true
  try {
    demoResult.value = await executePlatformDemoTask(demoQuery.value.trim())
    record('demo-task', demoResult.value, 'Demo task completed.')
  } catch (error) {
    localError.value = error.message || 'Demo task execution failed.'
  } finally {
    demoRunning.value = false
  }
}

const runAstro = async () => {
  localError.value = ''
  astroRunning.value = true
  try {
    astroResult.value = await runAstroDemo(astro)
    record('astro-demo', astroResult.value, 'Astro demonstration completed.')
  } catch (error) {
    localError.value = error.message || 'Astro demonstration failed.'
  } finally {
    astroRunning.value = false
  }
}

onMounted(refreshTools)
</script>

<style scoped>
.workspace { display: grid; gap: 16px; padding: 20px; }.workspace-header { display: flex; align-items: center; justify-content: space-between; gap: 14px; }.workspace-header h1 { margin: 0 0 4px; font-size: 1.2rem; }.workspace-header p { margin: 0; }.workspace-grid { display: grid; grid-template-columns: minmax(280px, .9fr) minmax(300px, 1.1fr); gap: 16px; }.stack, .panel { display: grid; align-content: start; gap: 10px; }.stack { gap: 16px; }.panel label { color: var(--color-text-muted); font-size: .8rem; }.panel textarea, .panel select, .panel input { width: 100%; box-sizing: border-box; padding: 9px; color: var(--color-text); background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }.panel pre { max-height: 300px; margin: 0; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; padding: 10px; color: var(--color-text); background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius-sm); font-size: .78rem; }.astro-form { display: flex; flex-wrap: wrap; align-items: end; gap: 10px; }.astro-form label { display: grid; gap: 5px; min-width: 150px; }.status-error { margin: 0; color: var(--color-danger); }button { cursor: pointer; }button:disabled { opacity: .55; cursor: not-allowed; }@media (max-width: 800px) { .workspace { padding: 14px; }.workspace-grid { grid-template-columns: 1fr; }.workspace-header { align-items: flex-start; flex-direction: column; } }
</style>
